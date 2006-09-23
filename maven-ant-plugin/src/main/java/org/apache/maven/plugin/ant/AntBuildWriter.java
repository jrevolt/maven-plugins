package org.apache.maven.plugin.ant;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.PathUtils;
import org.apache.tools.ant.Main;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;

/**
 * Write an <code>build.xml<code> for <a href="http://ant.apache.org">Ant</a> 1.6.2 or above.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public class AntBuildWriter
{
    /**
     * The default line indenter
     */
    protected static final int DEFAULT_INDENTATION_SIZE = 2;

    /**
     * The default build file name (build.xml)
     * @see Main#DEFAULT_BUILD_FILENAME
     */
    protected static final String DEFAULT_BUILD_FILENAME = Main.DEFAULT_BUILD_FILENAME;

    /**
     * The default build properties file name
     */
    protected static final String DEFAULT_PROPERTIES_FILENAME = "build.properties";

    private MavenProject project;

    private File localRepository;

    private Settings settings;

    /**
     * @param project
     * @param localRepository
     * @param settings
     */
    public AntBuildWriter( MavenProject project, File localRepository, Settings settings )
    {
        this.project = project;
        this.localRepository = localRepository;
        this.settings = settings;
    }

    // ----------------------------------------------------------------------
    // build.xml
    // ----------------------------------------------------------------------

    protected void writeBuildXml()
        throws IOException
    {
        // TODO: parameter
        FileWriter w = new FileWriter( new File( project.getBasedir(), DEFAULT_BUILD_FILENAME ) );

        XMLWriter writer = new PrettyPrintXMLWriter( w, StringUtils.repeat( " ", DEFAULT_INDENTATION_SIZE ), "UTF-8",
                                                     null );

        // ----------------------------------------------------------------------
        // <!-- comments -->
        // ----------------------------------------------------------------------

        writeHeader( writer );

        // ----------------------------------------------------------------------
        // <project/>
        // ----------------------------------------------------------------------

        writer.startElement( "project" );
        writer.addAttribute( "name", project.getArtifactId() );
        writer.addAttribute( "default", "jar" );
        writer.addAttribute( "basedir", "." );

        AntBuildWriterUtil.writeLineBreak( writer );

        // ----------------------------------------------------------------------
        // <property/>
        // ----------------------------------------------------------------------

        writeProperties( writer );

        // ----------------------------------------------------------------------
        // <path/>
        // ----------------------------------------------------------------------

        writeBuildPathDefinition( writer );

        // ----------------------------------------------------------------------
        // <target name="clean" />
        // ----------------------------------------------------------------------

        writeCleanTarget( writer );

        // ----------------------------------------------------------------------
        // <target name="compile" />
        // ----------------------------------------------------------------------

        List compileSourceRoots = removeEmptyCompileSourceRoots( project.getCompileSourceRoots() );
        writeCompileTarget( writer, compileSourceRoots );

        // ----------------------------------------------------------------------
        // <target name="compile-tests" />
        // ----------------------------------------------------------------------

        List testCompileSourceRoots = removeEmptyCompileSourceRoots( project.getTestCompileSourceRoots() );
        writeCompileTestsTarget( writer, testCompileSourceRoots );

        // ----------------------------------------------------------------------
        // <target name="test" />
        // ----------------------------------------------------------------------

        writeTestTargets( writer, testCompileSourceRoots );

        // ----------------------------------------------------------------------
        // <target name="jar" />
        // ----------------------------------------------------------------------
        // TODO: what if type is not JAR?
        writeJarTarget( writer );

        // ----------------------------------------------------------------------
        // <target name="get-deps" />
        // ----------------------------------------------------------------------
        writeGetDepsTarget( writer );

        writer.endElement(); // project

        IOUtil.close( w );
    }

    protected void writeBuildProperties()
        throws IOException
    {
        FileOutputStream os = new FileOutputStream( new File( project.getBasedir(), DEFAULT_PROPERTIES_FILENAME ) );
        Properties properties = new Properties();

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        addProperty( properties, "maven.build.finalName", PathUtils.toRelative( project.getBasedir(), project
            .getBuild().getFinalName() ) );

        // target
        addProperty( properties, "maven.build.dir", PathUtils.toRelative( project.getBasedir(), project.getBuild()
            .getDirectory() ) );

        // ${maven.build.dir}/classes
        addProperty( properties, "maven.build.outputDir", "${maven.build.dir}/"
            + PathUtils.toRelative( new File( project.getBasedir(), properties.getProperty( "maven.build.dir" ) ),
                                    project.getBuild().getOutputDirectory() ) );
        // src/main/java
        if ( !project.getCompileSourceRoots().isEmpty() )
        {
            String[] compileSourceRoots = (String[]) project.getCompileSourceRoots().toArray( new String[0] );
            for ( int i = 0; i < compileSourceRoots.length; i++ )
            {
                addProperty( properties, "maven.build.srcDir." + i, PathUtils.toRelative( project.getBasedir(),
                                                                                          compileSourceRoots[i] ) );
            }
        }
        // src/main/resources
        if ( project.getBuild().getResources() != null )
        {
            Resource[] array = (Resource[]) project.getBuild().getResources().toArray( new Resource[0] );
            for ( int i = 0; i < array.length; i++ )
            {
                addProperty( properties, "maven.build.resourceDir." + i, PathUtils.toRelative( project.getBasedir(),
                                                                                               array[i].getDirectory() ) );
            }
        }

        // ${maven.build.dir}/test-classes
        addProperty( properties, "maven.build.testOutputDir", "${maven.build.dir}/"
            + PathUtils.toRelative( new File( project.getBasedir(), properties.getProperty( "maven.build.dir" ) ),
                                    project.getBuild().getTestOutputDirectory() ) );
        // src/test/java
        if ( !project.getTestCompileSourceRoots().isEmpty() )
        {
            String[] compileSourceRoots = (String[]) project.getTestCompileSourceRoots().toArray( new String[0] );
            for ( int i = 0; i < compileSourceRoots.length; i++ )
            {
                addProperty( properties, "maven.build.testDir." + i, PathUtils.toRelative( project.getBasedir(),
                                                                                           compileSourceRoots[i] ) );
            }
        }
        // src/test/resources
        if ( project.getBuild().getTestResources() != null )
        {
            Resource[] array = (Resource[]) project.getBuild().getTestResources().toArray( new Resource[0] );
            for ( int i = 0; i < array.length; i++ )
            {
                addProperty( properties, "maven.build.testResourceDir." + i, PathUtils
                    .toRelative( project.getBasedir(), array[i].getDirectory() ) );
            }
        }

        // ----------------------------------------------------------------------
        // Settings properties
        // ----------------------------------------------------------------------

        addProperty( properties, "maven.settings.offline", String.valueOf( settings.isOffline() ) );
        addProperty( properties, "maven.settings.interactiveMode", String.valueOf( settings.isInteractiveMode() ) );
        addProperty( properties, "maven.repo.local", localRepository.getAbsolutePath() );

        properties.store( os, "Generated by Maven Ant Plugin" );
    }

    private void writeProperties( XMLWriter writer )
    {
        // TODO: optional in m1
        // TODO: USD properties
        AntBuildWriterUtil.writeCommentText( writer, "Build environnement properties", 1 );

        // ----------------------------------------------------------------------
        // File properties to override local properties
        // ----------------------------------------------------------------------

        writer.startElement( "property" );
        writer.addAttribute( "file", "${user.home}/.m2/maven.properties" );
        writer.endElement(); // property

        writer.startElement( "property" );
        writer.addAttribute( "file", "build.properties" );
        writer.endElement(); // property

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        AntBuildWriterUtil.writeLineBreak( writer, 2, 1 );

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.build.finalName" );
        writer.addAttribute( "value", project.getBuild().getFinalName() );
        writer.endElement(); // property

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.build.dir" );
        writer.addAttribute( "value", PathUtils.toRelative( project.getBasedir(), project.getBuild().getDirectory() ) );
        writer.endElement(); // property

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.build.outputDir" );
        writer.addAttribute( "value", "${maven.build.dir}/"
            + PathUtils.toRelative( new File( project.getBuild().getDirectory() ), project.getBuild()
                .getOutputDirectory() ) );
        writer.endElement(); // property

        if ( !project.getCompileSourceRoots().isEmpty() )
        {
            String[] compileSourceRoots = (String[]) project.getCompileSourceRoots().toArray( new String[0] );
            for ( int i = 0; i < compileSourceRoots.length; i++ )
            {
                writer.startElement( "property" );
                writer.addAttribute( "name", "maven.build.srcDir." + i );
                writer.addAttribute( "value", PathUtils.toRelative( project.getBasedir(), compileSourceRoots[i] ) );
                writer.endElement(); // property
            }
        }

        if ( project.getBuild().getResources() != null )
        {
            Resource[] array = (Resource[]) project.getBuild().getResources().toArray( new Resource[0] );
            for ( int i = 0; i < array.length; i++ )
            {
                writer.startElement( "property" );
                writer.addAttribute( "name", "maven.build.resourceDir." + i );
                writer.addAttribute( "value", PathUtils.toRelative( project.getBasedir(), array[i].getDirectory() ) );
                writer.endElement(); // property
            }
        }

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.build.testOutputDir" );
        writer.addAttribute( "value", "${maven.build.dir}/"
            + PathUtils.toRelative( new File( project.getBuild().getDirectory() ), project.getBuild()
                .getTestOutputDirectory() ) );
        writer.endElement(); // property

        if ( !project.getTestCompileSourceRoots().isEmpty() )
        {
            String[] compileSourceRoots = (String[]) project.getTestCompileSourceRoots().toArray( new String[0] );
            for ( int i = 0; i < compileSourceRoots.length; i++ )
            {
                writer.startElement( "property" );
                writer.addAttribute( "name", "maven.build.testDir." + i );
                writer.addAttribute( "value", PathUtils.toRelative( project.getBasedir(), compileSourceRoots[i] ) );
                writer.endElement(); // property
            }
        }

        if ( project.getBuild().getResources() != null )
        {
            Resource[] array = (Resource[]) project.getBuild().getResources().toArray( new Resource[0] );
            for ( int i = 0; i < array.length; i++ )
            {
                writer.startElement( "property" );
                writer.addAttribute( "name", "maven.build.testResourceDir." + i );
                writer.addAttribute( "value", PathUtils.toRelative( project.getBasedir(), array[i].getDirectory() ) );
                writer.endElement(); // property
            }
        }

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.test.reports" );
        writer.addAttribute( "value", "${maven.build.dir}/test-reports" );
        writer.endElement(); // property

        // ----------------------------------------------------------------------
        // Setting properties
        // ----------------------------------------------------------------------

        AntBuildWriterUtil.writeLineBreak( writer, 2, 1 );

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.repo.local" );
        writer.addAttribute( "value", localRepository.getAbsolutePath() );
        writer.endElement(); // property

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.settings.offline" );
        writer.addAttribute( "value", String.valueOf( settings.isOffline() ) );
        writer.endElement(); // property

        writer.startElement( "property" );
        writer.addAttribute( "name", "maven.settings.interactiveMode" );
        writer.addAttribute( "value", String.valueOf( settings.isInteractiveMode() ) );
        writer.endElement(); // property

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeBuildPathDefinition( XMLWriter writer )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Defining classpaths", 1 );

        writer.startElement( "path" );
        writer.addAttribute( "id", "build.classpath" );
        writer.startElement( "fileset" );
        writer.addAttribute( "dir", "${maven.repo.local}" );
        if ( !project.getCompileArtifacts().isEmpty() )
        {
            for ( Iterator i = project.getCompileArtifacts().iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                writer.startElement( "include" );
                writer.addAttribute( "name", PathUtils.toRelative( localRepository, artifact.getFile().getPath() ) );
                writer.endElement(); // include
            }
        }
        else
        {
            writer.startElement( "include" );
            writer.addAttribute( "name", "*.jar" );
            writer.endElement(); // include
        }
        writer.endElement(); // fileset
        writer.endElement(); // path

        writer.startElement( "path" );
        writer.addAttribute( "id", "build.test.classpath" );
        writer.startElement( "fileset" );
        writer.addAttribute( "dir", "${maven.repo.local}" );
        if ( !project.getTestArtifacts().isEmpty() )
        {
            for ( Iterator i = project.getTestArtifacts().iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                writer.startElement( "include" );
                writer.addAttribute( "name", PathUtils.toRelative( localRepository, artifact.getFile().getPath() ) );
                writer.endElement(); // include
            }
        }
        else
        {
            writer.startElement( "include" );
            writer.addAttribute( "name", "*.jar" );
            writer.endElement(); // include
        }
        writer.endElement(); // fileset
        writer.endElement(); // path

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeCleanTarget( XMLWriter writer )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Cleaning up target", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "clean" );
        writer.addAttribute( "description", "Clean the output directory" );

        writer.startElement( "delete" );
        writer.addAttribute( "dir", "${maven.build.dir}" );
        writer.endElement(); // delete

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeCompileTarget( XMLWriter writer, List compileSourceRoots )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Compilation target", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "compile" );
        writer.addAttribute( "depends", "get-deps" );
        writer.addAttribute( "description", "Compile the code" );

        writeCompileTasks( writer, project.getBasedir(), "${maven.build.outputDir}", compileSourceRoots, project
            .getBuild().getResources(), null, false );

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeCompileTestsTarget( XMLWriter writer, List testCompileSourceRoots )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Test-compilation target", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "compile-tests" );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "depends", "junit-present, compile", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "description", "Compile the test code", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "if", "junit.present", 2 );

        writeCompileTasks( writer, project.getBasedir(), "${maven.build.testOutputDir}", testCompileSourceRoots,
                           project.getBuild().getTestResources(), "${maven.build.outputDir}", true );

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeTestTargets( XMLWriter writer, List testCompileSourceRoots )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Run all tests", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "test" );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "depends", "junit-present, compile-tests", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "if", "junit.present", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "description", "Run the test cases", 2 );

        if ( !testCompileSourceRoots.isEmpty() )
        {
            writer.startElement( "mkdir" );
            writer.addAttribute( "dir", "${maven.test.reports}" );
            writer.endElement(); //mkdir

            writer.startElement( "junit" );
            writer.addAttribute( "printSummary", "yes" );
            writer.addAttribute( "haltonerror", "true" );
            writer.addAttribute( "haltonfailure", "true" );
            writer.addAttribute( "fork", "true" );
            writer.addAttribute( "dir", "." );

            writer.startElement( "sysproperty" );
            writer.addAttribute( "key", "basedir" );
            writer.addAttribute( "value", "." );
            writer.endElement(); // sysproperty

            writer.startElement( "formatter" );
            writer.addAttribute( "type", "xml" );
            writer.endElement(); // formatter

            writer.startElement( "formatter" );
            writer.addAttribute( "type", "plain" );
            writer.addAttribute( "usefile", "false" );
            writer.endElement(); // formatter

            writer.startElement( "classpath" );
            writer.startElement( "path" );
            writer.addAttribute( "refid", "build.test.classpath" );
            writer.endElement(); // path
            writer.startElement( "pathelement" );
            writer.addAttribute( "location", "${maven.build.outputDir}" );
            writer.endElement(); // pathelement
            writer.startElement( "pathelement" );
            writer.addAttribute( "location", "${maven.build.testOutputDir}" );
            writer.endElement(); // pathelement
            writer.endElement(); // classpath

            writer.startElement( "batchtest" );
            writer.addAttribute( "todir", "${maven.test.reports}" );

            String[] compileSourceRoots = (String[]) testCompileSourceRoots.toArray( new String[0] );
            for ( int i = 0; i < compileSourceRoots.length; i++ )
            {
                writer.startElement( "fileset" );
                writer.addAttribute( "dir", "${maven.build.testDir." + i + "}" );
                /* TODO: need to get these from the test plugin somehow?
                 UnitTest unitTest = project.getBuild().getUnitTest();
                 writeIncludesExcludes( writer, unitTest.getIncludes(), unitTest.getExcludes() );
                 // TODO: m1 allows additional test exclusions via maven.ant.excludeTests
                 */
                writeIncludesExcludes( writer, Collections.singletonList( "**/*Test.java" ), Collections
                    .singletonList( "**/*Abstract*Test.java" ) );
                writer.endElement(); // fileset
            }
            writer.endElement(); // batchtest

            writer.endElement(); // junit
        }
        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer, 2, 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "test-junit-present" );

        writer.startElement( "available" );
        writer.addAttribute( "classname", "junit.framework.Test" );
        writer.addAttribute( "property", "junit.present" );
        writer.endElement(); // available

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer, 2, 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "junit-present" );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "depends", "test-junit-present", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "unless", "junit.present", 2 );

        writer.startElement( "echo" );
        writer.writeText( StringUtils.repeat( "=", 35 ) + " WARNING " + StringUtils.repeat( "=", 35 ) );
        writer.endElement(); // echo

        writer.startElement( "echo" );
        writer.writeText( " Junit isn't present in your $ANT_HOME/lib directory. Tests not executed. " );
        writer.endElement(); // echo

        writer.startElement( "echo" );
        writer.writeText( StringUtils.repeat( "=", 79 ) );
        writer.endElement(); // echo

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private void writeJarTarget( XMLWriter writer )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Creation target", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "jar" );
        writer.addAttribute( "depends", "compile,test" );
        writer.addAttribute( "description", "Create the JAR" );

        writer.startElement( "jar" );
        writer.addAttribute( "jarfile", "${maven.build.dir}/${maven.build.finalName}.jar" );
        AntBuildWriterUtil.addWrapAttribute( writer, "jar", "basedir", "${maven.build.outputDir}", 3 );
        AntBuildWriterUtil.addWrapAttribute( writer, "jar", "excludes", "**/package.html", 3 );
        writer.endElement(); // jar

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    private static void writeCompileTasks( XMLWriter writer, File basedir, String outputDirectory,
                                          List compileSourceRoots, List resources, String additionalClassesDirectory,
                                          boolean isTest )
    {
        writer.startElement( "mkdir" );
        writer.addAttribute( "dir", outputDirectory );
        writer.endElement(); // mkdir

        if ( !compileSourceRoots.isEmpty() )
        {
            writer.startElement( "javac" );
            writer.addAttribute( "destdir", outputDirectory );
            AntBuildWriterUtil.addWrapAttribute( writer, "javac", "excludes", "**/package.html", 3 );
            AntBuildWriterUtil.addWrapAttribute( writer, "javac", "debug", "true", 3 ); // TODO: use compiler setting
            AntBuildWriterUtil.addWrapAttribute( writer, "javac", "deprecation", "true", 3 ); // TODO: use compiler setting
            AntBuildWriterUtil.addWrapAttribute( writer, "javac", "optimize", "false", 3 ); // TODO: use compiler setting

            String[] compileSourceRootsArray = (String[]) compileSourceRoots.toArray( new String[0] );
            for ( int i = 0; i < compileSourceRootsArray.length; i++ )
            {
                writer.startElement( "src" );
                writer.startElement( "pathelement" );
                if ( isTest )
                {
                    writer.addAttribute( "location", "${maven.build.testDir." + i + "}" );
                }
                else
                {
                    writer.addAttribute( "location", "${maven.build.srcDir." + i + "}" );
                }
                writer.endElement(); // pathelement
                writer.endElement(); // src
            }

            if ( additionalClassesDirectory == null )
            {
                writer.startElement( "classpath" );
                if ( isTest )
                {
                    writer.addAttribute( "refid", "build.test.classpath" );
                }
                else
                {
                    writer.addAttribute( "refid", "build.classpath" );
                }
                writer.endElement(); // classpath
            }
            else
            {
                writer.startElement( "classpath" );
                writer.startElement( "path" );
                if ( isTest )
                {
                    writer.addAttribute( "refid", "build.test.classpath" );
                }
                else
                {
                    writer.addAttribute( "refid", "build.classpath" );
                }
                writer.endElement(); // path
                writer.startElement( "pathelement" );
                writer.addAttribute( "location", additionalClassesDirectory );
                writer.endElement(); // pathelement
                writer.endElement(); // classpath
            }

            writer.endElement(); // javac
        }

        Resource[] array = (Resource[]) resources.toArray( new Resource[0] );
        for ( int i = 0; i < array.length; i++ )
        {
            Resource resource = array[i];

            if ( new File( resource.getDirectory() ).exists() )
            {
                String outputDir = outputDirectory;
                if ( resource.getTargetPath() != null && resource.getTargetPath().length() > 0 )
                {
                    outputDir = outputDir + "/" + resource.getTargetPath();

                    writer.startElement( "mkdir" );
                    writer.addAttribute( "dir", outputDir );
                    writer.endElement(); // mkdir
                }

                writer.startElement( "copy" );
                writer.addAttribute( "todir", outputDir );

                writer.startElement( "fileset" );
                if ( isTest )
                {
                    writer.addAttribute( "dir", "${maven.build.testResourceDir." + i + "}" );
                }
                else
                {
                    writer.addAttribute( "dir", "${maven.build.resourceDir." + i + "}" );
                }

                writeIncludesExcludes( writer, resource.getIncludes(), resource.getExcludes() );

                writer.endElement(); // fileset

                writer.endElement(); // copy
            }
        }
    }

    /**
     * @param writer
     */
    private void writeGetDepsTarget( XMLWriter writer )
    {
        AntBuildWriterUtil.writeCommentText( writer, "Download dependencies target", 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "test-offline" );

        writer.startElement( "condition" );
        writer.addAttribute( "property", "maven.mode.offline" );
        writer.startElement( "equals" );
        writer.addAttribute( "arg1", "${maven.settings.offline}" );
        writer.addAttribute( "arg2", "true" );
        writer.endElement(); // equals
        writer.endElement(); // condition
        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer, 2, 1 );

        writer.startElement( "target" );
        writer.addAttribute( "name", "get-deps" );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "depends", "test-offline", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "description", "Download all dependencies", 2 );
        AntBuildWriterUtil.addWrapAttribute( writer, "target", "unless", "maven.mode.offline", 2 ); // TODO: check, and differs from m1

        writer.startElement( "mkdir" );
        writer.addAttribute( "dir", "${maven.repo.local}" );
        writer.endElement(); // mkdir

        // TODO: proxy - probably better to use wagon!
        for ( Iterator i = project.getTestArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();

            // TODO: should the artifacthandler be used instead?
            String path = PathUtils.toRelative( localRepository, artifact.getFile().getPath() );

            File parentDirs = new File( path ).getParentFile();
            if ( parentDirs != null )
            {
                writer.startElement( "mkdir" );
                writer.addAttribute( "dir", "${maven.repo.local}/" + parentDirs.getPath() );
                writer.endElement(); // mkdir
            }

            for ( Iterator j = project.getRepositories().iterator(); j.hasNext(); )
            {
                Repository repository = (Repository) j.next();

                writer.startElement( "get" );
                writer.addAttribute( "src", repository.getUrl() + "/" + path );
                AntBuildWriterUtil.addWrapAttribute( writer, "get", "dest", "${maven.repo.local}/" + path, 3 );
                AntBuildWriterUtil.addWrapAttribute( writer, "get", "usetimestamp", "true", 3 );
                AntBuildWriterUtil.addWrapAttribute( writer, "get", "ignoreerrors", "true", 3 );
                writer.endElement(); // get
            }
        }

        writer.endElement(); // target

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    // ----------------------------------------------------------------------
    // Convenience methods
    // ----------------------------------------------------------------------

    /**
     * @param compileSourceRoots
     * @return not null list
     */
    private static List removeEmptyCompileSourceRoots( List compileSourceRoots )
    {
        List newCompileSourceRootsList = new ArrayList();
        if ( compileSourceRoots != null )
        {
            // copy as I may be modifying it
            for ( Iterator i = compileSourceRoots.iterator(); i.hasNext(); )
            {
                String srcDir = (String) i.next();
                if ( new File( srcDir ).exists() )
                {
                    newCompileSourceRootsList.add( srcDir );
                }
            }
        }

        return newCompileSourceRootsList;
    }

    /**
     * @param writer
     * @param includes
     * @param excludes
     */
    private static void writeIncludesExcludes( XMLWriter writer, List includes, List excludes )
    {
        for ( Iterator i = includes.iterator(); i.hasNext(); )
        {
            String include = (String) i.next();
            writer.startElement( "include" );
            writer.addAttribute( "name", include );
            writer.endElement(); // include
        }
        for ( Iterator i = excludes.iterator(); i.hasNext(); )
        {
            String exclude = (String) i.next();
            writer.startElement( "exclude" );
            writer.addAttribute( "name", exclude );
            writer.endElement(); // exclude
        }
    }

    /**
     * Write comment in the file header
     *
     * @param writer
     */
    private static void writeHeader( XMLWriter writer )
    {
        AntBuildWriterUtil.writeLineBreak( writer );

        AntBuildWriterUtil.writeCommentLineBreak( writer );
        AntBuildWriterUtil.writeComment( writer, "Ant build file (http://ant.apache.org/) for Ant 1.6.2 or above." );
        AntBuildWriterUtil.writeCommentLineBreak( writer );

        AntBuildWriterUtil.writeLineBreak( writer );

        AntBuildWriterUtil.writeCommentLineBreak( writer );
        AntBuildWriterUtil.writeComment( writer, StringUtils.repeat( "=", 21 ) + " - DO NOT EDIT THIS FILE! - "
            + StringUtils.repeat( "=", 21 ) );
        AntBuildWriterUtil.writeCommentLineBreak( writer );
        AntBuildWriterUtil.writeComment( writer, " " );
        AntBuildWriterUtil.writeComment( writer, "Any modifications will be overwritten." );
        AntBuildWriterUtil.writeComment( writer, " " );
        DateFormat dateFormat = DateFormat.getDateTimeInstance( DateFormat.SHORT, DateFormat.SHORT, Locale.US );
        AntBuildWriterUtil.writeComment( writer, "Generated by Maven Ant Plugin on "
            + dateFormat.format( new Date( System.currentTimeMillis() ) ) );
        AntBuildWriterUtil.writeComment( writer, "See: http://maven.apache.org/plugins/maven-ant-plugin/" );
        AntBuildWriterUtil.writeComment( writer, " " );
        AntBuildWriterUtil.writeCommentLineBreak( writer );

        AntBuildWriterUtil.writeLineBreak( writer );
    }

    /**
     * Put a property in properties defined by a name and a value
     *
     * @param properties
     * @param name
     * @param value
     */
    private static void addProperty( Properties properties, String name, String value )
    {
        properties.put( name, StringUtils.isNotEmpty( value ) ? value : "" );
    }
}
