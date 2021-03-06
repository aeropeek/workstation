package org.janelia.workstation.core.events.model;

import org.janelia.model.domain.DomainObject;

/**
 * A domain object or part of its object graph has changed in some way.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class DomainObjectChangeEvent extends DomainObjectEvent {
    public DomainObjectChangeEvent(DomainObject domainObject) {
        super(domainObject);
    }
}
