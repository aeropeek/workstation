package org.janelia.workstation.browser.actions;

import javax.swing.Action;

import org.janelia.model.domain.sample.Sample;
import org.janelia.workstation.common.nb_action.DomainObjectNodeAction;
import org.janelia.workstation.core.actions.ViewerContext;
import org.janelia.workstation.integration.spi.domain.ContextualActionBuilder;
import org.janelia.workstation.integration.spi.domain.ContextualActionUtils;
import org.openide.util.lookup.ServiceProvider;

/**
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ServiceProvider(service = ContextualActionBuilder.class, position = 510)
public class RerunSamplesBuilder implements ContextualActionBuilder {

    private static final RerunSamplesActionHarness action = new RerunSamplesActionHarness();

    @Override
    public boolean isCompatible(Object obj) {
        return obj instanceof Sample;
    }

    @Override
    public Action getAction(Object obj) {
        return action;
    }

    public static class RerunSamplesActionHarness extends DomainObjectNodeAction {

        private RerunSamplesAction innerAction;

        @Override
        public String getName() {
            return null;
        }

        @Override
        public void setViewerContext(ViewerContext viewerContext) {
            this.innerAction = RerunSamplesAction.createAction(viewerContext.getDomainObjectList());
            if (innerAction!=null) {
                ContextualActionUtils.setName(this, (String) innerAction.getValue(Action.NAME));
                ContextualActionUtils.setVisible(this, true);
            }
            else {
                ContextualActionUtils.setVisible(this, false);
            }
        }

        @Override
        protected void executeAction() {
            innerAction.actionPerformed(null);
        }

    }
}