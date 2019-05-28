package org.janelia.workstation.gui.large_volume_viewer.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.eventbus.Subscribe;

import org.apache.commons.lang3.tuple.Pair;
import org.janelia.it.jacs.model.user_data.tiledMicroscope.CoordinateToRawTransform;
import org.janelia.model.domain.DomainConstants;
import org.janelia.model.domain.DomainObjectComparator;
import org.janelia.model.domain.Reference;
import org.janelia.model.domain.tiledMicroscope.BulkNeuronStyleUpdate;
import org.janelia.model.domain.tiledMicroscope.TmNeuronMetadata;
import org.janelia.model.domain.tiledMicroscope.TmProtobufExchanger;
import org.janelia.model.domain.tiledMicroscope.TmReviewTask;
import org.janelia.model.domain.tiledMicroscope.TmSample;
import org.janelia.model.domain.tiledMicroscope.TmWorkspace;
import org.janelia.model.domain.workspace.TreeNode;
import org.janelia.workstation.core.api.AccessManager;
import org.janelia.workstation.core.api.DomainMgr;
import org.janelia.workstation.core.api.DomainModel;
import org.janelia.workstation.core.events.Events;
import org.janelia.workstation.core.events.lifecycle.ConsolePropsLoaded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton for managing the Tiled Microscope Domain Model and related data access.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class TiledMicroscopeDomainMgr {

    private static final Logger LOG = LoggerFactory.getLogger(TiledMicroscopeDomainMgr.class);

    // Singleton
    private static TiledMicroscopeDomainMgr instance;
    
    public static TiledMicroscopeDomainMgr getDomainMgr() {
        if (instance==null) {
            instance = new TiledMicroscopeDomainMgr();
        }
        return instance;
    }

    private TiledMicroscopeRestClient client;
    
    private TiledMicroscopeDomainMgr() {
        this.client = new TiledMicroscopeRestClient();
    }

    private DomainModel getModel() {
        return DomainMgr.getDomainMgr().getModel();
    }

    public List<String> getSamplePaths() throws Exception {
        return client.getTmSamplePaths();
    }

    public void setSamplePaths(List<String> paths) throws Exception {
        client.updateSamplePaths(paths);
    }
    
    public TmSample getSample(Long sampleId) throws Exception {
        LOG.debug("getSample(sampleId={})",sampleId);
        return getModel().getDomainObject(TmSample.class, sampleId);
    }

    public TmSample getSample(TmWorkspace workspace) throws Exception {
        LOG.debug("getSample({})",workspace);
        return getSample(workspace.getSampleRef().getTargetId());
    }

    public TmSample createSample(String name, String filepath) throws Exception {
        LOG.debug("createTiledMicroscopeSample(name={}, filepath={})", name, filepath);
        Map<String,Object> constants = client.getTmSampleConstants(filepath);
        if (constants != null) {
            TreeNode tmSampleFolder = getModel().getDefaultWorkspaceFolder(DomainConstants.NAME_TM_SAMPLE_FOLDER, true);

            TmSample sample = new TmSample();
            sample.setOwnerKey(AccessManager.getSubjectKey());
            sample.setName(name);
            sample.setFilepath(filepath);
            Map originMap = (Map)constants.get("origin");
            List<Integer> origin = new ArrayList<>();
            origin.add ((Integer)originMap.get("x"));
            origin.add ((Integer)originMap.get("y"));
            origin.add ((Integer)originMap.get("z"));            
            Map scalingMap = (Map)constants.get("scaling");
            List<Double> scaling = new ArrayList<>();
            scaling.add ((Double)scalingMap.get("x"));
            scaling.add ((Double)scalingMap.get("y"));
            scaling.add ((Double)scalingMap.get("z"));
            
            sample.setOrigin(origin);
            sample.setScaling(scaling);
            if (constants.get("numberLevels") instanceof Integer) {
                sample.setNumImageryLevels(((Integer)constants.get("numberLevels")).longValue());
            } else {
                sample.setNumImageryLevels(Long.parseLong((String)constants.get("numberLevels")));
            }

            // call out to server to get origin/scaling information
            TmSample persistedSample = save(sample);

            // Server should have put the sample in the Samples root folder. Refresh the Samples folder to show it in the explorer.
            getModel().invalidate(tmSampleFolder);

            return persistedSample;
        } else {
            LOG.error("Problem creating sample; no sample constants available for sample {} at {}.", name, filepath);
            return null;
        }
    }

    public TmSample save(TmSample sample) throws Exception {
        LOG.debug("save({})",sample);
        TmSample canonicalObject;
        synchronized (this) {
            canonicalObject = getModel().putOrUpdate(sample.getId()==null ? client.create(sample) : client.update(sample));
        }
        if (sample.getId()==null) {
            getModel().notifyDomainObjectCreated(canonicalObject);
        }
        else {
            getModel().notifyDomainObjectChanged(canonicalObject);
        }
        return canonicalObject;
    }

    public void remove(TmSample sample) throws Exception {
        LOG.debug("remove({})",sample);
        client.remove(sample);
        getModel().notifyDomainObjectRemoved(sample);
    }

    public List<TmWorkspace> getWorkspacesSortedByCurrentPrincipal(Long sampleId) throws Exception {
        Collection<TmWorkspace> workspaces = client.getTmWorkspacesForSample(sampleId);
        List<TmWorkspace> canonicalObjects = DomainMgr.getDomainMgr().getModel().putOrUpdate(workspaces, false);
        // Sorting this when the data is queried is a bit tricky
        // because the DomainObjectComparator returns objects owned by the current principal first
        // Also I would prefer to make this more obvious in the server call that the sorting is taking place (goinac).
        Collections.sort(canonicalObjects, new DomainObjectComparator(AccessManager.getSubjectKey()));
        return canonicalObjects;
    }

    public TmWorkspace getWorkspace(Long workspaceId) throws Exception {
        LOG.debug("getWorkspace(workspaceId={})",workspaceId);
        TmWorkspace workspace = getModel().getDomainObject(TmWorkspace.class, workspaceId);
        if (workspace==null) {
            throw new Exception("Workspace with id="+workspaceId+" does not exist");
        }
        return workspace;
    }
    
    public TmWorkspace createWorkspace(Long sampleId, String name) throws Exception {
        LOG.debug("createWorkspace(sampleId={}, name={})", sampleId, name);
        TmSample sample = getSample(sampleId);
        if (sample==null) {
            throw new IllegalArgumentException("TM sample does not exist: "+sampleId);
        }

        TreeNode defaultWorkspaceFolder = getModel().getDefaultWorkspaceFolder(DomainConstants.NAME_TM_WORKSPACE_FOLDER, true);

        TmWorkspace workspace = new TmWorkspace();
        workspace.setOwnerKey(AccessManager.getSubjectKey());
        workspace.setName(name);
        workspace.setSampleRef(Reference.createFor(TmSample.class, sampleId));
        workspace = save(workspace);
        
        // Server should have put the workspace in the Workspaces root folder. Refresh the Workspaces folder to show it in the explorer.
        getModel().invalidate(defaultWorkspaceFolder);
        
        // Also invalidate the sample, so that the Explorer tree can be updated 
        getModel().invalidate(sample);
        
        return workspace;
    }

    public TmWorkspace copyWorkspace(TmWorkspace workspace, String name, String assignOwner) throws Exception {
        LOG.debug("copyWorkspace(workspace={}, name={}, neuronOwner={})", workspace, name, assignOwner);
        TmSample sample = getSample(workspace.getSampleId());
        if (sample==null) {
            throw new IllegalArgumentException("TM sample does not exist: "+workspace.getSampleId());
        }

        TreeNode defaultWorkspaceFolder = getModel().getDefaultWorkspaceFolder(DomainConstants.NAME_TM_WORKSPACE_FOLDER, true);

        TmWorkspace workspaceCopy = client.copy(workspace, name, assignOwner);

        // Server should have put the new workspace in the Workspaces root folder. Refresh the Workspaces folder to show it in the explorer.
        getModel().invalidate(defaultWorkspaceFolder);
        
        // Also invalidate the sample, so that the Explorer tree can be updated 
        getModel().invalidate(sample);
        
        return workspaceCopy;
    }

    public TmWorkspace save(TmWorkspace workspace) throws Exception {
        LOG.debug("save({})", workspace);
        TmWorkspace canonicalObject;
        synchronized (this) {
            canonicalObject = getModel().putOrUpdate(workspace.getId()==null ? client.create(workspace) : client.update(workspace));
        }
        if (workspace.getId()==null) {
            getModel().notifyDomainObjectCreated(canonicalObject);
        }
        else {
            getModel().notifyDomainObjectChanged(canonicalObject);
        }
        return canonicalObject;
    }

    public void remove(TmWorkspace workspace) throws Exception {
        LOG.debug("remove({})", workspace);
        client.remove(workspace);
        getModel().notifyDomainObjectRemoved(workspace);
    }
    
    public List<TmNeuronMetadata> getWorkspaceNeurons(Long workspaceId) throws Exception {
        LOG.debug("getWorkspaceNeurons(workspaceId={})",workspaceId);
        TmProtobufExchanger exchanger = new TmProtobufExchanger();
        List<TmNeuronMetadata> neurons = new ArrayList<>();
        for(Pair<TmNeuronMetadata, InputStream> pair : client.getWorkspaceNeuronPairs(workspaceId)) {
            TmNeuronMetadata neuronMetadata = pair.getLeft();
            exchanger.deserializeNeuron(pair.getRight(), neuronMetadata);
            LOG.trace("Got neuron {} with payload '{}'", neuronMetadata.getId(), neuronMetadata);
            neurons.add(neuronMetadata);
        }
        LOG.trace("Loaded {} neurons for workspace {}", neurons.size(), workspaceId);
        return neurons;
    }

    public TmNeuronMetadata saveMetadata(TmNeuronMetadata neuronMetadata) throws Exception {
        LOG.debug("save({})", neuronMetadata);
        TmNeuronMetadata savedMetadata;
        if (neuronMetadata.getId()==null) {
            savedMetadata = client.createMetadata(neuronMetadata);
            getModel().notifyDomainObjectCreated(savedMetadata);
        }
        else {
            savedMetadata = client.updateMetadata(neuronMetadata);
            getModel().notifyDomainObjectChanged(savedMetadata);
        }
        return savedMetadata;
    }

    public List<TmNeuronMetadata> saveMetadata(List<TmNeuronMetadata> neuronList) throws Exception {
        LOG.debug("save({})", neuronList);
        for(TmNeuronMetadata tmNeuronMetadata : neuronList) {
            if (tmNeuronMetadata.getId()==null) {
                throw new IllegalArgumentException("Bulk neuron creation is currently unsupported");
            }
        }
        List<TmNeuronMetadata> updatedMetadata = client.updateMetadata(neuronList);
        for(TmNeuronMetadata tmNeuronMetadata : updatedMetadata) {
            getModel().notifyDomainObjectChanged(tmNeuronMetadata);
        }
        return updatedMetadata;
    }
    
    public TmNeuronMetadata save(TmNeuronMetadata neuronMetadata) throws Exception {
        LOG.debug("save({})", neuronMetadata);
        TmProtobufExchanger exchanger = new TmProtobufExchanger();
        InputStream protobufStream = new ByteArrayInputStream(exchanger.serializeNeuron(neuronMetadata));
        TmNeuronMetadata savedMetadata;
        if (neuronMetadata.getId()==null) {
            savedMetadata = client.create(neuronMetadata, protobufStream);
            getModel().notifyDomainObjectCreated(savedMetadata);
        }
        else {
            savedMetadata = client.update(neuronMetadata, protobufStream);
            getModel().notifyDomainObjectChanged(savedMetadata);
        }
        // We assume that the neuron data was saved on the server, but it only returns metadata for efficiency. We
        // already have the data, so let's copy it over into the new object.
        exchanger.copyNeuronData(neuronMetadata, savedMetadata);
        return savedMetadata;
    }
    
    public TmReviewTask save(TmReviewTask reviewTask) throws Exception {
        LOG.debug("save({})", reviewTask);
        if (reviewTask.getId()==null) {
            reviewTask = client.create(reviewTask);
        } else {
            reviewTask = client.update(reviewTask);
        }
        getModel().notifyDomainObjectChanged(reviewTask);
        return reviewTask;
    }
    
    public void remove(TmReviewTask reviewTask) throws Exception {
        LOG.debug("remove({})", reviewTask);
        client.remove(reviewTask);
        getModel().notifyDomainObjectRemoved(reviewTask);
    }
    
    public void updateNeuronStyles(BulkNeuronStyleUpdate bulkNeuronStyleUpdate) throws Exception {
        client.updateNeuronStyles(bulkNeuronStyleUpdate);
    }
    
    public void remove(TmNeuronMetadata tmNeuron) throws Exception {
        LOG.debug("remove({})", tmNeuron);
        TmNeuronMetadata neuronMetadata = new TmNeuronMetadata();
        neuronMetadata.setId(tmNeuron.getId());
        client.remove(neuronMetadata);
        getModel().notifyDomainObjectRemoved(neuronMetadata);
    }
    
    public List<TmReviewTask> getReviewTasks() throws Exception {
        LOG.debug("getReviewTasks()");
        List<TmReviewTask> reviewTasks = getModel().getAllDomainObjectsByClass(TmReviewTask.class);
        return reviewTasks;
    }

    public void bulkEditNeuronTags(List<TmNeuronMetadata> neurons, List<String> tags, boolean tagState) throws Exception {
        LOG.debug("bulkEditTags({})", neurons);
        client.changeTags(neurons, tags, tagState);
    }

    public CoordinateToRawTransform getCoordToRawTransform(String basePath) throws Exception {
        LOG.debug("getCoordToRawTransform({})", basePath);
        return client.getLvvCoordToRawTransform(basePath);
    }

    public byte[] getRawTextureBytes(String basePath, int[] viewerCoord, int[] dimensions, int channel) throws Exception {
        LOG.debug("getTextureBytes({}, viewerCoord={}, dimensions={})", basePath, viewerCoord, dimensions);
        return client.getRawTextureBytes(basePath, viewerCoord, dimensions, channel);
    }

    public String getNearestChannelFilesURL(String basePath, int[] viewerCoord) {
        LOG.debug("getNearestChannelFilesURL({}, viewerCoord={})", basePath, viewerCoord);
        return client.getNearestChannelFilesURL(basePath, viewerCoord);
    }
}
