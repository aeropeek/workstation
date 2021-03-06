package org.janelia.workstation.gui.passive_3d;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.ProgressMonitor;

import org.janelia.workstation.gui.large_volume_viewer.ColorButtonPanel;
import org.janelia.workstation.gui.passive_3d.filter.MatrixFilter3D;
import org.janelia.workstation.integration.util.FrameworkAccess;
import org.janelia.workstation.common.gui.support.Icons;
import org.janelia.workstation.common.gui.support.StateDrivenIconToggleButton;
import org.janelia.workstation.core.workers.IndeterminateProgressMonitor;
import org.janelia.workstation.core.workers.SimpleWorker;
import org.janelia.console.viewerapi.model.ImageColorModel;
import org.janelia.console.viewerapi.color_slider.SliderPanel;
import org.janelia.console.viewerapi.controller.ColorModelListener;
import org.janelia.workstation.gui.viewer3d.texture.TextureDataI;
import org.janelia.workstation.gui.viewer3d.volume_builder.VolumeDataChunk;

/**
 * Represents the group of controls.  Actions against controls are encapsulated
 * together, and getters against this group can be used to fetch them for
 * layout purposes, anywhere.
 * 
 * @author fosterl
 */
public class Snapshot3dControls {
    private SliderPanel sliderPanel;
    private ColorButtonPanel colorButtonPanel;
    private Collection<TextureDataI> textureDatas;
    private List<JComponent> components;
    private AbstractButton sharedColorModelButton;
    private Snapshot3d view;
    private SnapshotVolumeBrick brick;
    private ImageColorModel independentColorModel;
    private ImageColorModel sharedColorModel;
    private ImageColorModel activeColorModel;
    private List<Action> filterActions;
    private ViewUpdateListener viewUpdateListener;
    
    public Snapshot3dControls(
            Collection<TextureDataI> textureDatas,
            ImageColorModel independentColorModel,
            ImageColorModel sharedColorModel,
            Snapshot3d view,
            SnapshotVolumeBrick brick 
    ) {
        this.textureDatas = textureDatas;
        this.view = view;
        this.brick = brick;
        this.independentColorModel = independentColorModel;
        this.sharedColorModel = sharedColorModel;
        this.activeColorModel = independentColorModel;

        initComponents();
    }

    public void cleanup() {
        for ( JComponent component: components ) {
            Container parent = component.getParent();
            parent.remove( component );            
        }
    }
    
    /**
     * @return the sliderPanel
     */
    public SliderPanel getSliderPanel() {
        return sliderPanel;
    }

    /**
     * Fetch-back the buttons for controlling colors.  Can place that as needed.
     * 
     * @return the addSubButton
     */
    public ColorButtonPanel getColorButtonPanel() {
        return colorButtonPanel;
    }
    
    public boolean[] getAddSubChoices() {        
        boolean[] rtnVal = new boolean[ activeColorModel.getChannelCount() ];
        int i = 0;
        for ( JCheckBox checkbox: colorButtonPanel.getCheckboxes() ) {
            rtnVal[ i++ ] = checkbox.isSelected();
        }
        return rtnVal;
    }
    
    /**
     * Controls about whether color sliders are synchronized or not.
     * @return the button for placement.
     */
    public AbstractButton getSharedColorModelButton() {
        return sharedColorModelButton;
    }
    
    public boolean isSharingColorModel() {
        return sharedColorModelButton.isSelected();
    }

    /**
     * @return the activeColorModel
     */
    public ImageColorModel getActiveColorModel() {
        return activeColorModel;
    }
    
    public void setIndependentColorModel( boolean isIndependent ) {
        if ( isIndependent ) {
            activeColorModel = independentColorModel;
        }
        else {
            activeColorModel = sharedColorModel;
        }
        if ( getSliderPanel() != null ) {
            getSliderPanel().setImageColorModel( activeColorModel );
        }
        if ( getColorButtonPanel() != null ) {
            getColorButtonPanel().setImageColorModel( activeColorModel );
        }
        view.validate();
        view.repaint();
    }
    
    public boolean isIndendentColorModel() {
        return activeColorModel == independentColorModel;
    }

    /**
     * @param activeColorModel the activeColorModel to set
     */
    public void setActiveColorModel(ImageColorModel activeColorModel) {
        this.activeColorModel = activeColorModel;
    }
    
    /**
     * @return the filterActions
     */
    public List<Action> getFilterActions() {
        return filterActions;
    }
    
    /** After a filter has been run, use this method to avoid further such changes. */
    public void setFilterActiveState( boolean hasBeenFiltered ) {        
        for ( Action action: filterActions ) {
            action.setEnabled( ! hasBeenFiltered );
        }
    }
    
    private void initComponents() {
        brick.setControls( this );

        this.components = new ArrayList<>();
        this.sliderPanel = new SliderPanel( activeColorModel );
        colorButtonPanel = new ColorButtonPanel( activeColorModel, 1 );
        this.components.add( getSliderPanel());
        this.components.add( getColorButtonPanel() );
        getSliderPanel().guiInit();
        getSliderPanel().updateLockButtons();
        getSliderPanel().setVisible(true);
        
        viewUpdateListener = new ViewUpdateListener( view );
        
        // One more button to allow the user to 'synchronize' the two color
        // models.
        StateDrivenIconToggleButton localSharedColorModelBtn = new StateDrivenIconToggleButton( Icons.getIcon( "link.png" ), Icons.getIcon( "link_break.png" ) );
        localSharedColorModelBtn.setToolTipText( "Share main viewer color controls." );  
        localSharedColorModelBtn.setSelected( false );
        sharedColorModelButton = localSharedColorModelBtn;
        sharedColorModelButton.addActionListener(
                new ColorModelSwapListener( this, viewUpdateListener )
        );
        components.add( sharedColorModelButton );        
        getColorButtonPanel().add(sharedColorModelButton);

        activeColorModel.addColorModelListener(viewUpdateListener);
        
        filterActions = new ArrayList<>();
        getFilterActions().add( new FilterMatrixAction( textureDatas, view, MatrixFilter3D.SPHERE_3_3_3, "Filter 3x3x3 Round" ) );
        getFilterActions().add( new FilterMatrixAction( textureDatas, view, MatrixFilter3D.AVG_MATRIX_3_3_3, "Filter 3x3x3 Averaging" ) );
        getFilterActions().add( new FilterMatrixAction( textureDatas, view, MatrixFilter3D.GAUSS_65_85_85, "Filter Gauss 5x5x5 Sigmas = (0.65,0.85,0.85)" ) );
        //getFilterActions().add( new FilterMatrixAction( textureDatas, view, MatrixFilter3D.GAUSS_5_5_5, "Filter 5x5x5 Gauss" ) );
    }
    
    private static class FilterMatrixAction extends AbstractAction {
        private Collection<TextureDataI> textureDatas;
        private Snapshot3d view;
        private double[] matrix;
        public FilterMatrixAction( Collection<TextureDataI> textureDatas, Snapshot3d view, double[] matrix, String actionName ) {
            this.textureDatas = textureDatas;
            this.view = view;
            putValue(Action.NAME, actionName);    
            this.matrix = matrix;
        }
        
        @Override
        public void actionPerformed( final ActionEvent ae ) {
            // Need to apply a filter to each texture.
            //       Pop out the byte array, pass that into the filtering
            //       apparatus, run the filter, and pop it back in.
            final IndeterminateProgressMonitor progressMonitor = 
                    new IndeterminateProgressMonitor(
                            view, getValue(Action.NAME), "Smoothing data..."
                    );
            SimpleWorker sw = new SimpleWorker() {
                private static final String FILTER_METHOD_PREFIX = " Filter";

                @Override
                protected void doStuff() throws Exception {
                    view.setHasBeenFiltered( true );
                    String actionName = (String)getValue(Action.NAME);
                    String oldLabelText = view.getLabelText();
                    int filterPos = oldLabelText.indexOf(FILTER_METHOD_PREFIX);
                    if (filterPos > -1) {
                        oldLabelText = oldLabelText.substring(0, filterPos);
                    }
                    view.setLabelText(oldLabelText + " " + actionName);
                    filterTextureDatas( progressMonitor );
                }

                @Override
                protected void hadSuccess() {
                    view.setHasBeenFiltered( true );
                    view.reLaunch( textureDatas );
                    view.validate();
                    view.repaint();
                }

                @Override
                protected void hadError(Throwable error) {
                    view.setHasBeenFiltered( false );
                    FrameworkAccess.handleException(error);
                }
                
            };
            sw.setProgressMonitor(progressMonitor);
            sw.execute();
        } 

        private void filterTextureDatas(final ProgressMonitor progressMonitor) throws IllegalArgumentException {
            for (TextureDataI textureData : textureDatas) {
                if (textureData.getTextureData().getVolumeChunks().length > 1) {
                    throw new IllegalArgumentException("Filtering algorithm can handle only a single chunk.");
                }
                for (VolumeDataChunk chunk : textureData.getTextureData().getVolumeChunks()) {
                    byte[] data = chunk.getData();
                    MatrixFilter3D filter = new MatrixFilter3D(matrix, textureData.getByteOrder());
                    filter.setProgressMonitor(progressMonitor);
                    byte[] newBytes
                            = filter.filter(
                                    data, 
                                    textureData.getPixelByteCount(),
                                    textureData.getChannelCount(),
                                    textureData.getSx(),
                                    textureData.getSy(),
                                    textureData.getSz()
                            );
                    chunk.setData(newBytes);
                }
            }
        }
    }

    private static class ViewUpdateListener implements ActionListener, ColorModelListener {
        private Snapshot3d view;
        
        public ViewUpdateListener( Snapshot3d view ) {
            this.view = view;
        }
        
        @Override
        public void actionPerformed(ActionEvent ae) {
            updateView();
        }

        @Override
        public void colorModelChanged() {
            updateView();
        }

        private void updateView() {
            view.validate();
            view.repaint();
        }

    }

    private static class ColorModelSwapListener implements ActionListener {
        private final Snapshot3dControls controls;
        private final ViewUpdateListener listener;
        public ColorModelSwapListener( Snapshot3dControls controls, ViewUpdateListener viewUpdateListener ) {
            this.controls = controls;
            this.listener = viewUpdateListener;
        }
        
        @Override
        public void actionPerformed(ActionEvent ae) {
            controls.getActiveColorModel().removeColorModelListener( listener );
            controls.setIndependentColorModel( ! controls.isIndendentColorModel() );
            controls.getActiveColorModel().addColorModelListener( listener );
        }
    }
}
