package org.janelia.console.viewerapi.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.janelia.console.viewerapi.Command;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;
import org.janelia.console.viewerapi.model.NeuronEdge;
import org.janelia.console.viewerapi.model.NeuronModel;
import org.janelia.console.viewerapi.model.NeuronSet;
import org.janelia.console.viewerapi.model.NeuronVertex;

/**
 * Applies Command design pattern to the act of manually adding one vertex to a neuron
 * @author brunsc
 */
public class AppendNeuronVertexCommand 
extends AbstractUndoableEdit
implements UndoableEdit, Command
{
    private final NeuronSet workspace;
    // private final NeuronModel neuron;
    // private NeuronVertex parentVertex;
    private NeuronVertex newVertex = null; // caches the newly added vertex
    private final float[] coordinates;
    private final float radius;
    // private final VertexAdder parentCommand; // maintains a linked list, to help resolve stale parent vertices after serial undo/redo
    
    public AppendNeuronVertexCommand(
            NeuronSet workspace,
            // NeuronModel neuron, 
            // NeuronVertex parentVertex,
            // VertexAdder parentCommand, // to help unravel serial undo/redo, with replaced parent vertices, in case parentVertex is stale
            float[] micronXyz,
            float radius) 
    {
        this.workspace = workspace;
        // this.neuron = neuron;
        // this.parentVertex = parentVertex;
        // this.parentCommand = parentCommand;
        this.coordinates = micronXyz;
        this.radius = radius;
    }
    
    // Command-like semantics execute is almost a synonym for redo()
    @Override
    public boolean execute() {
        // refreshParent();
        NeuronVertex parentVertex = workspace.getPrimaryAnchor(); // always build from current primary
        if (parentVertex == null)
            return false;
        NeuronModel neuron = workspace.getNeuronForAnchor(parentVertex);
        if (neuron == null)
            return false;
        newVertex = neuron.appendVertex(parentVertex, coordinates, radius);
        if (newVertex == null)
            return false;
        workspace.setPrimaryAnchor(newVertex);
        workspace.getPrimaryAnchorObservable().notifyObservers();
        return true;
    }
    
    /*
    @Override
    public NeuronVertex getAddedVertex() {
        return newVertex;
    }
     */
    
    /*
    private void refreshParent() {
        if (parentCommand != null) { // check in case serial undo/redo made parentVertex stale
            NeuronVertex updatedParent = parentCommand.getAddedVertex();
            if (updatedParent != parentVertex)
                parentVertex = updatedParent; // update link
        }        
    }
     */
    
    /*
    @Override
    public NeuronVertex getParentVertex() {
        // refreshParent();
        return parentVertex;
    }
     */
    
    @Override
    public String getPresentationName() {
        return "Append Neuron Anchor";
    }
    
    @Override
    public void redo() {
        super.redo(); // raises exception if canRedo() is false
        if (! execute())
            die(); // Something went wrong. This Command object is no longer useful.
    }
    
    @Override
    public void undo() {
        super.undo(); // raises exception if canUndo() is false
        try {
            NeuronModel neuron = workspace.getNeuronForAnchor(newVertex);
            if (neuron == null)
                throw new Exception();
            // Find adjacent vertex, so we could update parent
            List<NeuronVertex> neighbors = new ArrayList<>();
            for (NeuronEdge edge : neuron.getEdges()) {
                Iterator<NeuronVertex> it = edge.iterator();
                NeuronVertex v1 = it.next();
                NeuronVertex v2 = it.next();
                if (v1 == newVertex)
                    neighbors.add(v2);
                else if (v2 == newVertex)
                    neighbors.add(v1);
            }
            if (neighbors.size() != 1)
                throw new Exception();
            NeuronVertex parent = neighbors.get(0);
            if (! neuron.deleteVertex(newVertex))
                throw new Exception();
            workspace.setPrimaryAnchor(parent);
            workspace.getPrimaryAnchorObservable().notifyObservers();
        } catch (Exception exc) {
            // Something went wrong. Perhaps this anchor no longer exists
            die(); // This Command object is no longer useful
        }
        newVertex = null;
    }

}
