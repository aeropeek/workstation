package org.janelia.workstation.gui.large_volume_viewer.style;

import java.util.HashMap;
import java.util.Map;

/**
 * Handy container for style information.
 *
 * @author fosterl
 */
public class NeuronStyleModel {
    private Map<Long,NeuronStyle> styles = new HashMap<>();
    
    public void setStyleMap(Map<Long,NeuronStyle> styles) {
        this.styles = styles;
    }
    
    public NeuronStyle get( Long id ) {
        return styles.get( id );
    }
    
    public void put( Long id, NeuronStyle style ) {
        styles.put( id, style );
    }

    public void remove( Long id ) {
        styles.remove( id );
    }
    
    public boolean containsKey( Long id ) {
        return styles.containsKey(id);
    }
    
    public void clear() {
        styles.clear();
    }
}
