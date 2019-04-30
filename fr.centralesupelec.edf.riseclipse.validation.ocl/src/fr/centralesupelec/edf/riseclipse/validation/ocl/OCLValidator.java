/**
 *  Copyright (c) 2019 CentraleSupélec & EDF.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  This file is part of the RiseClipse tool
 *  
 *  Contributors:
 *      Computer Science Department, CentraleSupélec
 *      EDF R&D
 *  Contacts:
 *      dominique.marcadet@centralesupelec.fr
 *      aurelie.dehouck-neveu@edf.fr
 *  Web site:
 *      http://wdi.supelec.fr/software/RiseClipse/
 */
package fr.centralesupelec.edf.riseclipse.validation.ocl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.ocl.pivot.internal.utilities.PivotEnvironmentFactory;
import org.eclipse.ocl.pivot.resource.BasicProjectManager;
import org.eclipse.ocl.pivot.resource.CSResource;
import org.eclipse.ocl.pivot.validation.ComposedEValidator;
import org.eclipse.ocl.xtext.completeocl.validation.CompleteOCLEObjectValidator;

import fr.centralesupelec.edf.riseclipse.util.IRiseClipseConsole;
import fr.centralesupelec.edf.riseclipse.util.RiseClipseFatalException;

public class OCLValidator {
    
    private @NonNull EPackage modelPackage;
    // workaround for bug 486872
    private @NonNull Path oclTempFile;
    private PivotEnvironmentFactory environmentFactoryForChecking;
    
    // see below
    private static final Logger logger = Logger.getLogger( CompleteOCLEObjectValidator.class );
    
    public OCLValidator( @NonNull EPackage modelPackage, @NonNull IRiseClipseConsole console ) {
        this.modelPackage = modelPackage;
        
        // standalone
        // see http://help.eclipse.org/mars/topic/org.eclipse.ocl.doc/help/PivotStandalone.html
        // *.uml support not required
        //org.eclipse.ocl.pivot.uml.UMLStandaloneSetup.init();
        // *.ocl Complete OCL documents support required
        org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup.doSetup();
        // *.oclinecore support not required
        //org.eclipse.ocl.xtext.oclinecore.OCLinEcoreStandaloneSetup.doSetup();
        // *.oclstdlib OCL Standard Library support required
        org.eclipse.ocl.xtext.oclstdlib.OCLstdlibStandaloneSetup.doSetup();
      
        try {
            oclTempFile = Files.createTempFile( "allConstraints", ".ocl" );
        }
        catch( IOException e ) {
            throw new RiseClipseFatalException( "Unable to create temporary file", e );
        }
        
        // CompleteOCLEObjectValidator display error messages on its logger.
        // We want to use our console for this, so we block the error level
        logger.setLevel( Level.FATAL );
    }

    // Does not work now
    // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=486872
//    public boolean addOCLDocument( URI oclURI, IRiseClipseConsole console ) {
//        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, oclURI, environmentFactory );
//        validator.addChild( oclValidator );
//        return true;
//    }
    
    public boolean addOCLDocument( @NonNull String oclFileName, @NonNull IRiseClipseConsole console ) {
        return addOCLDocument( new File( oclFileName ), console );
    }

    public boolean addOCLDocument( @NonNull File oclFile, @NonNull IRiseClipseConsole console ) {
        if( ! oclFile.exists() ) {
            console.error( oclFile + " does not exist" );
            return false;
        }
        if( ! oclFile.isFile() ) {
            console.error( oclFile + " is not a file" );
            return false;
        }
        if( ! oclFile.canRead() ) {
            console.error( oclFile + " cannot be read" );
            return false;
        }

        URI uri = URI.createFileURI( oclFile.getAbsolutePath() );
        if( uri == null ) {
            throw new RiseClipseFatalException( "Unable to create URI for temporary file", null );
        }
        
        // We want to check the validity of OCL files
        // So, we have to do it now, before concatenating it to oclTempFile
        // Errors are detected in CompleteOCLEObjectValidator.initialize()
        // but there is no way to get back errors, we only know that there is
        // a problem because initialize() returns false.
        // The code below is taken from CompleteOCLEObjectValidator.initialize()
        if( environmentFactoryForChecking == null ) {
            environmentFactoryForChecking = new PivotEnvironmentFactory( new BasicProjectManager(), null );
        }
        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, uri, environmentFactoryForChecking );
        if( ! oclValidator.initialize() ) {
            console.error( "syntax error in " + oclFile + " (it will be ignored):" );
            @NonNull
            ResourceSet resourceSet = environmentFactoryForChecking.getResourceSet();
            CSResource xtextResource = (CSResource) resourceSet.getResource( uri, true );
            EList< Diagnostic > errors = xtextResource.getErrors();
            for( Diagnostic error : errors ) {
                console.error( "  " + error.getMessage() );
            }
            return false;
        }
        
        //Path path = FileSystems.getDefault().getPath( oclFileName ).toAbsolutePath();
        String path = oclFile.getAbsolutePath();
        // Take care of Windows paths
        if( path.charAt( 0 ) != '/' ) {
            path = "/" + path.replace( '\\', '/' );
        }
        try {
            BufferedWriter o = Files.newBufferedWriter( oclTempFile, StandardOpenOption.APPEND );
            //o.write( "import \'" + path + "\'\n" );
            o.write( "import \'file:" + path + "\'\n" );
            o.close();
        }
        catch( IOException e ) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void prepare( @NonNull ComposedEValidator validator, @NonNull IRiseClipseConsole console ) {
        URI uri = URI.createFileURI( oclTempFile.toFile().getAbsolutePath() );
        if( uri == null ) {
            throw new RiseClipseFatalException( "Unable to create URI for temporary file", null );
        }
        // Do not re-use environmentFactoryForChecking, it will gave errors
        PivotEnvironmentFactory environmentFactory = new PivotEnvironmentFactory( new BasicProjectManager(), null );
        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, uri, environmentFactory );
        validator.addChild( oclValidator );    
    }
    
}
