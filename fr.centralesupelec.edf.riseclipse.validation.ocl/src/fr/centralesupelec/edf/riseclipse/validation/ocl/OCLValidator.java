/*
*************************************************************************
**  Copyright (c) 2016-2022 CentraleSupélec & EDF.
**  All rights reserved. This program and the accompanying materials
**  are made available under the terms of the Eclipse Public License v2.0
**  which accompanies this distribution, and is available at
**  https://www.eclipse.org/legal/epl-v20.html
** 
**  This file is part of the RiseClipse tool
**  
**  Contributors:
**      Computer Science Department, CentraleSupélec
**      EDF R&D
**  Contacts:
**      dominique.marcadet@centralesupelec.fr
**      aurelie.dehouck-neveu@edf.fr
**  Web site:
**      https://riseclipse.github.io
*************************************************************************
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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.ocl.pivot.resource.CSResource;
import org.eclipse.ocl.pivot.utilities.OCL;
import org.eclipse.ocl.pivot.validation.ComposedEValidator;
import org.eclipse.ocl.xtext.completeocl.validation.CompleteOCLEObjectValidator;

import fr.centralesupelec.edf.riseclipse.util.IRiseClipseConsole;

public class OCLValidator {
    
    private static final String OCL_SETUP_CATEGORY = "OCL/Setup";

    private @NonNull EPackage modelPackage;
    // workaround for bug 486872
    private @NonNull Path oclTempFile = Path.of( ".", "" );  // Initialization to remove a Sonar warning
    private @NonNull OCL ocl = OCL.newInstance( OCL.NO_PROJECTS );
    // see below
    private static final Logger logger = Logger.getLogger( CompleteOCLEObjectValidator.class );  // NOSONAR
    
    public OCLValidator( @NonNull EPackage modelPackage, @NonNull IRiseClipseConsole console ) {
        console.debug( OCL_SETUP_CATEGORY, 0, "Building OCLValidator for ", modelPackage.getName() );
        
        this.modelPackage = modelPackage;
        // standalone
        // see http://help.eclipse.org/mars/topic/org.eclipse.ocl.doc/help/PivotStandalone.html
        // *.uml support not required
        //org.eclipse.ocl.pivot.uml.UMLStandaloneSetup.init();  // NOSONAR
        // *.ocl Complete OCL documents support required
        org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup.doSetup();
        // *.oclinecore support not required
        //org.eclipse.ocl.xtext.oclinecore.OCLinEcoreStandaloneSetup.doSetup();  // NOSONAR
        // *.oclstdlib OCL Standard Library support required
        org.eclipse.ocl.xtext.oclstdlib.OCLstdlibStandaloneSetup.doSetup();
      
        try {
            oclTempFile = Files.createTempFile( "allConstraints", ".ocl" );
        }
        catch( IOException e ) {
            console.emergency( OCL_SETUP_CATEGORY, 0, "Unable to create temporary file: got IOException ", e );
            return;
        }
        
        // CompleteOCLEObjectValidator display error messages on its logger.
        // We want to use our console for this, so we block the error level
        logger.setLevel( Level.FATAL );
    }

    // Does not work now (last tested: 7 October 2021)
    // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=486872
//    public boolean addOCLDocument( URI oclURI, IRiseClipseConsole console ) {                                                    // NOSONAR
//        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, oclURI, environmentFactory );  // NOSONAR
//        validator.addChild( oclValidator );                                                                                      // NOSONAR
//        return true;                                                                                                             // NOSONAR
//    }                                                                                                                            // NOSONAR
    
    public boolean addOCLDocument( @NonNull String oclFileName, @NonNull IRiseClipseConsole console ) {
        return addOCLDocument( new File( oclFileName ), console );
    }

    public boolean addOCLDocument( @NonNull File oclFile, @NonNull IRiseClipseConsole console ) {
        console.debug( OCL_SETUP_CATEGORY, 0, "addOCLDocument for ", oclFile.getName() );
        
        if( ! oclFile.exists() ) {
            console.error( OCL_SETUP_CATEGORY, 0, oclFile, " does not exist" );
            return false;
        }
        if( ! oclFile.isFile() ) {
            console.error( OCL_SETUP_CATEGORY, 0, oclFile, " is not a file" );
            return false;
        }
        if( ! oclFile.canRead() ) {
            console.error( OCL_SETUP_CATEGORY, 0, oclFile, " cannot be read" );
            return false;
        }
        console.info( OCL_SETUP_CATEGORY, 0, "Loading file ", oclFile.getName(), " in RiseClipse" );

        URI oclUri = URI.createFileURI( oclFile.getAbsolutePath() );
        if( oclUri == null ) {
            console.emergency( OCL_SETUP_CATEGORY, 0, "Unable to create URI for temporary file" );
            return false;
        }
        
        // We want to check the validity of OCL files
        // So, we have to do it now, before concatenating it to oclTempFile
        CSResource oclResource = null;
        try {
        	oclResource = ocl.getCSResource( oclUri );
        }
        catch( IOException e ) {
            console.emergency( OCL_SETUP_CATEGORY, 0, "Unable to read OCL file" );
            return false;
        }
        if( ! oclResource.getErrors().isEmpty() ) {
            console.error( OCL_SETUP_CATEGORY, 0, "syntax error in ", oclFile, " (it will be ignored):" );
            EList< Diagnostic > errors = oclResource.getErrors();
            for( Diagnostic error : errors ) {
                console.error( OCL_SETUP_CATEGORY, 0, "  ", error.getMessage() );
            }
            return false;
        }
        
        String path = oclFile.getAbsolutePath();
        // Take care of Windows paths
        if( path.charAt( 0 ) != '/' ) {
            path = "/" + path.replace( '\\', '/' );  // NOSONAR
        }
        try( BufferedWriter o = Files.newBufferedWriter( oclTempFile, StandardOpenOption.APPEND )) {
            o.write( "import \'file:" + path + "\'\n" );
        }
        catch( IOException e ) {
            console.emergency( OCL_SETUP_CATEGORY, 0, "Unable to write temporary file: got IOException ", e );
        }
        return true;
    }

    public void prepare( @NonNull ComposedEValidator validator, @NonNull IRiseClipseConsole console ) {
        URI uri = URI.createFileURI( oclTempFile.toFile().getAbsolutePath() );
        if( uri == null ) {
            console.emergency( OCL_SETUP_CATEGORY, 0, "Unable to create URI for temporary file" );
            return;
        }
        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, uri );
        validator.addChild( oclValidator );    
    }
    
}
