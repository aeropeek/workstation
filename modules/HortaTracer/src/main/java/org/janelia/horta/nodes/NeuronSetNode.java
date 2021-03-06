package org.janelia.horta.nodes;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.commons.io.FilenameUtils;
import org.janelia.console.viewerapi.model.NeuronModel;
import org.janelia.console.viewerapi.model.NeuronSet;
import org.janelia.horta.loader.DroppedFileHandler;
import org.janelia.horta.loader.GZIPFileLoader;
import org.janelia.horta.loader.SwcLoader;
import org.janelia.horta.loader.TarFileLoader;
import org.janelia.horta.loader.TgzFileLoader;
import org.openide.ErrorManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.datatransfer.PasteType;
import org.openide.util.lookup.Lookups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Christopher Bruns
 */
public class NeuronSetNode extends AbstractNode
{
    private final NeuronSet neuronList;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public NeuronSetNode(final NeuronSet neuronList) {
        super(Children.create(new NeuronSetChildFactory(neuronList), true), Lookups.singleton(neuronList));
        this.neuronList = neuronList;
        String name = neuronList.getName();
        setDisplayName(name);

        neuronList.getNameChangeObservable().addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg)
            {
                setDisplayName(neuronList.getName());
            }
        });
    }
    
    // Context menu for NeuronSetNode'
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> result = new ArrayList<>();
        result.add(new ManualUpdateNeuronsAction(neuronList));
        return result.toArray(new Action[result.size()]);
    }
    
    
    // Allow to drop SWC files on List, to add neurons
    @Override
    public PasteType getDropType(final Transferable transferable, int action, int index) 
    {
        final DroppedFileHandler droppedFileHandler = new DroppedFileHandler();
        droppedFileHandler.addLoader(new GZIPFileLoader());
        droppedFileHandler.addLoader(new TarFileLoader());
        droppedFileHandler.addLoader(new TgzFileLoader());        
        final SwcLoader swcLoader = new SwcLoader(neuronList);
        droppedFileHandler.addLoader(swcLoader);
       
        return new PasteType() {
            @Override
            public Transferable paste() throws IOException
            {
                try {
                    List<File> fileList = (List) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : fileList) {
                        droppedFileHandler.handleFile(f);
                    }
                    // Update after asynchronous load completes
                    swcLoader.runAfterLoad(new Runnable() {
                    @Override
                    public void run()
                    {
                        if (! neuronList.getMembershipChangeObservable().hasChanged())
                            return;
                        // Update models after drop.
                        neuronList.getMembershipChangeObservable().notifyObservers();
                        // force repaint - just once per drop action though.
                        triggerRepaint();
                    }
                });
                } catch (UnsupportedFlavorException ex) {
                    Exceptions.printStackTrace(ex);
                }
                return null;
            }
        };
    }
    
    private void triggerRepaint() {
        HortaWorkspaceNode parentNode = (HortaWorkspaceNode)(getParentNode());
        if (parentNode != null)
            parentNode.triggerRepaint();
    }
    
    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage("org/janelia/horta/images/neuron_group.png");
    }    
    
    @Override
    public Image getOpenedIcon(int i) {
        return getIcon(i);
    }
    
    public int getSize() {return neuronList.size();}
    
    @Override 
    protected Sheet createSheet() { 
        Sheet sheet = Sheet.createDefault(); 
        Sheet.Set set = Sheet.createPropertiesSet(); 
        try { 
            Property prop;
            // size
            prop = new PropertySupport.Reflection(this, int.class, "getSize", null); 
            prop.setName("size"); 
            set.put(prop); 
        } 
        catch (NoSuchMethodException ex) {
            ErrorManager.getDefault(); 
        } 
        sheet.put(set); 
        return sheet; 
    }

    private static class NeuronSetChildFactory extends ChildFactory<NeuronModel>
    {
        private final NeuronSet neuronList;
        
        public NeuronSetChildFactory(NeuronSet neuronList) {
            this.neuronList = neuronList;
            neuronList.getMembershipChangeObservable().addObserver(new Observer() {
                @Override
                public void update(Observable o, Object arg)
                {
                    refresh(false);
                }
            });
        }

        @Override
        protected boolean createKeys(List<NeuronModel> toPopulate)
        {
            toPopulate.addAll(neuronList);
            return true;
        }

        @Override
        protected Node createNodeForKey(NeuronModel key) {
            return new NeuronModelNode(key, neuronList.isReadOnly());
        }
    }
    
    
    private static class ManualUpdateNeuronsAction extends AbstractAction
    {
        private final NeuronSet neurons;
        
        public ManualUpdateNeuronsAction(NeuronSet neurons) 
        {
            putValue(NAME, "Refresh these neurons now");
            this.neurons = neurons;
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            // 1) Mark everything dirty in depth-first order
            for (NeuronModel neuron : neurons) {
                neuron.getGeometryChangeObservable().setChanged();
                neuron.getVertexesRemovedObservable().setChanged();
                neuron.getVertexCreatedObservable().setChanged();
                neuron.getVisibilityChangeObservable().setChanged();
                neuron.getColorChangeObservable().setChanged();
            }
            neurons.getMembershipChangeObservable().setChanged();
            neurons.getNameChangeObservable().setChanged();
            // 2) Notify observers in depth-first order
            for (NeuronModel neuron : neurons) {
                neuron.getGeometryChangeObservable().notifyObservers();
                // neuron.getVertexesRemovedObservable().notifyObservers();
                // neuron.getMembersAddedObservable().notifyObservers();
                neuron.getVisibilityChangeObservable().notifyObservers();
                neuron.getColorChangeObservable().notifyObservers();
            }
            neurons.getMembershipChangeObservable().notifyObservers();
            neurons.getNameChangeObservable().notifyObservers();
        }
        
    }
    
}
