/**
 *  Copyright (c) 2018 CentraleSupélec & EDF.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EValidator.SubstitutionLabelProvider;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.ocl.pivot.internal.utilities.PivotEnvironmentFactory;
import org.eclipse.ocl.pivot.resource.BasicProjectManager;
import org.eclipse.ocl.pivot.validation.ComposedEValidator;
import org.eclipse.ocl.xtext.completeocl.validation.CompleteOCLEObjectValidator;

import fr.centralesupelec.edf.riseclipse.util.IRiseClipseConsole;

public class OCLValidator {
    
    private EPackage modelPackage;
    private ComposedEValidator validator;
    private PivotEnvironmentFactory environmentFactory;
    // workaround for bug 486872
    private Path oclTempFile;
    
    public OCLValidator( EPackage modelPackage, boolean standalone ) {
        this.modelPackage = modelPackage;
        
        if( standalone ) {
            // see http://help.eclipse.org/mars/topic/org.eclipse.ocl.doc/help/PivotStandalone.html
            // *.uml support not required
            //org.eclipse.ocl.pivot.uml.UMLStandaloneSetup.init();
            // *.ocl Complete OCL documents support required
            org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup.doSetup();
            // *.oclinecore support not required
            //org.eclipse.ocl.xtext.oclinecore.OCLinEcoreStandaloneSetup.doSetup();
            // *.oclstdlib OCL Standard Library support required
            org.eclipse.ocl.xtext.oclstdlib.OCLstdlibStandaloneSetup.doSetup();
        }
      
        this.environmentFactory = new PivotEnvironmentFactory( new BasicProjectManager(), null );
        
        validator = ComposedEValidator.install( modelPackage );
        
        try {
            oclTempFile = Files.createTempFile( "allConstraints", ".ocl" );
        }
        catch( IOException e ) {
            e.printStackTrace();
        }
    }

    // Does not work now
    // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=486872
//    public boolean addOCLDocument( URI oclURI, IRiseClipseConsole console ) {
//        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, oclURI, environmentFactory );
//        validator.addChild( oclValidator );
//        return true;
//    }
    
    public boolean addOCLDocument( String oclFileName, IRiseClipseConsole console ) {
        //Path path = FileSystems.getDefault().getPath( oclFileName ).toAbsolutePath();
        try {
            BufferedWriter o = Files.newBufferedWriter( oclTempFile, StandardOpenOption.APPEND );
            //o.write( "import \'" + path + "\'\n" );
            o.write( "import \'" + oclFileName + "\'\n" );
            o.close();
        }
        catch( IOException e ) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void validate( Resource resource, final AdapterFactory adapter, IRiseClipseConsole console ) {
        // workaround for bug 486872
        
        // An existing CompleteOCLEObjectValidator may already be present, with the same or others OCL constraints
        // We have to remove it first
        ArrayList< EValidator > validatorsToRemove = new ArrayList<>();
        for( EValidator e : validator.getChildren() ) {
            if( e instanceof CompleteOCLEObjectValidator ) {
                validatorsToRemove.add( e );
            }
        }
        for( EValidator e : validatorsToRemove ) {
            validator.removeChild( e );
        }
        
        URI uri = URI.createFileURI( oclTempFile.toFile().getAbsolutePath() );
        CompleteOCLEObjectValidator oclValidator = new CompleteOCLEObjectValidator( modelPackage, uri, environmentFactory );
        validator.addChild( oclValidator );
       
        Map<Object, Object> context = new HashMap< Object, Object >();
        SubstitutionLabelProvider substitutionLabelProvider = new EValidator.SubstitutionLabelProvider() {
            
            @Override
            public String getValueLabel( EDataType eDataType, Object value ) {
                return Diagnostician.INSTANCE.getValueLabel( eDataType, value );
            }
            
            @Override
            public String getObjectLabel( EObject eObject ) {
                IItemLabelProvider labelProvider = ( IItemLabelProvider ) adapter.adapt( eObject, IItemLabelProvider.class );
                return labelProvider.getText( eObject );
            }
            
            @Override
            public String getFeatureLabel( EStructuralFeature eStructuralFeature ) {
                return Diagnostician.INSTANCE.getFeatureLabel( eStructuralFeature );
            }
        };
        context.put(EValidator.SubstitutionLabelProvider.class, substitutionLabelProvider );

        for( int n = 0; n < resource.getContents().size(); ++n ) {
            Diagnostic diagnostic = Diagnostician.INSTANCE.validate( resource.getContents().get( n ), context );
            
            if( diagnostic.getSeverity() == Diagnostic.ERROR || diagnostic.getSeverity() == Diagnostic.WARNING ) {
                //EObject root = ( EObject ) diagnostic.getData().get( 0 );
                //URI uri = root.eResource().getURI();
                //console.error( "in file " + uri.lastSegment() );
                for( Iterator< Diagnostic > i = diagnostic.getChildren().iterator(); i.hasNext(); ) {
                    Diagnostic childDiagnostic = i.next();
                    switch( childDiagnostic.getSeverity() ) {
                    case Diagnostic.ERROR:
                    case Diagnostic.WARNING:
                        List< ? > data = childDiagnostic.getData();
                        EObject object = ( EObject ) data.get( 0 );
                        if( data.size() == 1 ) {
                            console.error( "\t" + childDiagnostic.getMessage() );
                        }
                        else if( data.get( 1 ) instanceof EAttribute ) {
                            EAttribute attribute = ( EAttribute ) data.get( 1 );
                            console.error( "\tAttribute " + attribute.getName() + " of " + substitutionLabelProvider.getObjectLabel( object ) + " : " + childDiagnostic.getChildren().get( 0 ).getMessage() );
                        }
                        else {
                            console.error( "\t" + childDiagnostic.getMessage() );
                        }
                    }
                }
            }
        }
    }
}
