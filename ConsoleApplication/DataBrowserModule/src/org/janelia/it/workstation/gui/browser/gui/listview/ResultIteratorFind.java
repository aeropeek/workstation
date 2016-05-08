package org.janelia.it.workstation.gui.browser.gui.listview;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.workstation.gui.browser.model.search.ResultIterator;

/**
 * Searches a ResultIterator to find objects matching some string
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public abstract class ResultIteratorFind {

    private final ResultIterator resultIterator;

    public ResultIteratorFind(ResultIterator resultIterator) {
        this.resultIterator = resultIterator;
    }

    /**
     * Execute the search and return the first matching object found.
     * This method may request additional results from the server and thus should be
     * run in a background thread.
     * @return first match, or null if no match is found
     */
    public DomainObject find() {
        while (resultIterator.hasNext()) {
            DomainObject domainObject = resultIterator.next();
            if (domainObject!=null && matches(domainObject)) {
                return domainObject;
            }
        }
        return null;
    }

    /**
     * Implement this method to describe how an object should be matched.
     * @param currObject object to match against
     * @return true if the object matches the search string
     */
    protected abstract boolean matches(DomainObject currObject);
}
