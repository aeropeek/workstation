/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.gui.browser.nb_action;

import org.janelia.it.workstation.gui.browser.ConsoleApp;
import org.janelia.it.workstation.gui.framework.console.nb_action.SearchActionDelegate;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ActionID(
        category = "Search",
        id = "PatternAnnotationSearchAction"
)
@ActionRegistration(
        displayName = "#CTL_PatternAnnotationSearchAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/Search", position = 1100)
})
@Messages("CTL_PatternAnnotationSearchAction=Pattern Annotation Search")
public final class PatternAnnotationSearchAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        ConsoleApp.getConsoleApp().getPatternSearchDialog().showDialog();
    }
}