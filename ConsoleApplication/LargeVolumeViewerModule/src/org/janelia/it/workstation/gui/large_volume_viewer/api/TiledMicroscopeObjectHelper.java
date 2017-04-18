package org.janelia.it.workstation.gui.large_volume_viewer.api;

import org.janelia.it.jacs.integration.framework.domain.DomainObjectHelper;
import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmSample;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmDirectedSession;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmWorkspace;
import org.janelia.it.workstation.gui.large_volume_viewer.nodes.TmSampleNode;
import org.janelia.it.workstation.gui.large_volume_viewer.nodes.TmSessionNode;
import org.janelia.it.workstation.gui.large_volume_viewer.nodes.TmWorkspaceNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;

/**
 * A helper for making Tiled Microscope objects interoperable with other core modules.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ServiceProvider(service = DomainObjectHelper.class, path = DomainObjectHelper.DOMAIN_OBJECT_LOOKUP_PATH)
public class TiledMicroscopeObjectHelper implements DomainObjectHelper {

    @Override
    public boolean isCompatible(DomainObject domainObject) {
        return isCompatible(domainObject.getClass());
    }

    @Override
    public boolean isCompatible(Class<? extends DomainObject> clazz) {
        if (TmSample.class.isAssignableFrom(clazz)) {
            return true;
        }
        else if (TmWorkspace.class.isAssignableFrom(clazz)) {
            return true;
        }
        else if (TmDirectedSession.class.isAssignableFrom(clazz)) {
            return true;
        }
        return false;
    }
    
    @Override
    public Node getNode(DomainObject domainObject, ChildFactory parentChildFactory) throws Exception {
        if (TmSample.class.isAssignableFrom(domainObject.getClass())) {
            return new TmSampleNode(parentChildFactory, (TmSample)domainObject);
        }
        else if (TmWorkspace.class.isAssignableFrom(domainObject.getClass())) {
            return new TmWorkspaceNode(parentChildFactory, (TmWorkspace)domainObject);
        }
        else if (TmDirectedSession.class.isAssignableFrom(domainObject.getClass())) {
            return new TmSessionNode(parentChildFactory, (TmDirectedSession)domainObject);
        }
        else {
            throw new IllegalArgumentException("Domain class not supported: "+domainObject);
        }    
    }
    
    @Override
    public String getLargeIcon(DomainObject domainObject) {
        if (TmSample.class.isAssignableFrom(domainObject.getClass())) {
            return "folder_files_large.png";
        }
        else if (TmWorkspace.class.isAssignableFrom(domainObject.getClass())) {
            return "workspace_large.png";
        }
        else if (TmDirectedSession.class.isAssignableFrom(domainObject.getClass())) {
            return "monitor_large.png";
        }
        else {
            throw new IllegalArgumentException("Domain class not supported: "+domainObject);
        }
    }

    @Override
    public boolean supportsRemoval(DomainObject domainObject) {
        if (TmSample.class.isAssignableFrom(domainObject.getClass())) {
            return true;
        }
        else if (TmWorkspace.class.isAssignableFrom(domainObject.getClass())) {
            return true;
        }
        else {
            return false;
        }
    }
    
    @Override
    public void remove(DomainObject domainObject) throws Exception {
        TiledMicroscopeDomainMgr mgr = TiledMicroscopeDomainMgr.getDomainMgr();
        if (TmSample.class.isAssignableFrom(domainObject.getClass())) {
            mgr.remove((TmSample)domainObject);
        }
        else if (TmWorkspace.class.isAssignableFrom(domainObject.getClass())) {
            mgr.remove((TmWorkspace)domainObject);
        }
        else if (TmDirectedSession.class.isAssignableFrom(domainObject.getClass())) {
            mgr.remove((TmDirectedSession)domainObject);
        }
        else {
            throw new IllegalArgumentException("Domain class not supported: "+domainObject);
        }
    }

}
