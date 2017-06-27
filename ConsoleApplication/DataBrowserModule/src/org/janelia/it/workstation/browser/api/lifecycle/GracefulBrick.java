package org.janelia.it.workstation.browser.api.lifecycle;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ResourceBundle;

import javax.swing.JOptionPane;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.janelia.it.workstation.browser.api.http.HttpClientManager;
import org.janelia.it.workstation.browser.gui.support.WindowLocator;
import org.janelia.it.workstation.browser.util.SystemInfo;
import org.janelia.it.workstation.browser.util.Utils;
import org.openide.LifecycleManager;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GracefulBrick {

    private static final Logger log = LoggerFactory.getLogger(GracefulBrick.class);
    
    public void brickAndUninstall() throws Exception {

        log.info("Places.getUserDirectory: "+Places.getUserDirectory());
        log.info("user.dir: "+System.getProperty("user.dir")); // TODO: delete this before uninstall
        log.info("netbeans.home: "+System.getProperty("netbeans.home"));
        
        final String configFile = "config/app.conf";
        File sysWideConfig = InstalledFileLocator.getDefault().locate(configFile, "org.janelia.it.workstation", false);
        log.debug("Found system config at {}", sysWideConfig);
        
        if (!isBricked()) {
            return;
        }
        
        log.info("THIS CLIENT IS BRICKED. PROCEEDING TO FORCED UNINSTALL.");

        String helpPage = "manual/";
        File uninstaller = null;
        if (SystemInfo.isMac) {
            helpPage = "manual/macosx_upgrade.html";
            String installDir = sysWideConfig.getAbsolutePath().split("\\.app")[0]+".app";
            uninstaller = new File(installDir, "uninstall.command");
        }
        else if (SystemInfo.isWindows) {
            helpPage = "manual/windows_upgrade.html";
            String cp = sysWideConfig.getAbsolutePath();
            String installDir = cp.substring(0,cp.indexOf("JaneliaWorkstation")+"JaneliaWorkstation".length());
            uninstaller = new File(installDir, "uninstall.exe");
        }
        else if (SystemInfo.isLinux) {
            helpPage = "manual/linux_upgrade.html";
            String cp = sysWideConfig.getAbsolutePath();
            String installDir = cp.substring(0,cp.indexOf("JaneliaWorkstation")+"JaneliaWorkstation".length());
            uninstaller = new File(installDir, "uninstall.sh");
        }
        else {
            log.error("Unknown system: "+SystemInfo.OS_NAME);
            helpPage = "upgrade";
        }
        
        final String helpUrl = String.format("http://workstation.int.janelia.org/%s", helpPage);
        final String simpleHelpUrl = "http://workstation.int.janelia.org/upgrade";
        
        String html = "<html><body width='420'>" +
        "<p>This version of the Workstation is no longer supported and must be manually upgraged to the latest release.</p>" +
        "<br>" +
        "<p>When you press the Continue button below, the Workstation will exit and you will be taken to the following web page, which describes how to install the new version: " + simpleHelpUrl + 
        "</p>" +
        "</body></html>";
      
        String[] buttons = { "Continue" };
        JOptionPane.showOptionDialog(WindowLocator.getMainFrame(), html,
              "Manual Update Required", JOptionPane.WARNING_MESSAGE, 0, null, buttons, buttons[0]);
        
        // Delete any logs to ensure that the install directory is properly cleaned up
        if (uninstaller!=null) {
            log.info("Deleting logs in install directory: "+uninstaller.getParentFile());
            deleteLogs(uninstaller.getParentFile());
        }
        
//        final File uninstallerFile = uninstaller;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {

//                log.info("Executing uninstaller: "+uninstallerFile);
//                if (uninstallerFile!=null && uninstallerFile.exists()) {
//                    // Execute the uninstaller
//                    try {
//                        Runtime.getRuntime().exec(uninstallerFile.getAbsolutePath());
//                    }
//                    catch (IOException e) {
//                        // Ignore. It's too late now!
//                    }
//                }

                Utils.openUrlInBrowser(helpUrl);
            }
        });
        
        LifecycleManager.getDefault().exit(0);
    }

    private static boolean deleteLogs(File dir) {
        File[] dirFiles = dir.listFiles();
        if (dirFiles != null) {
            for (File dirFile : dirFiles) {
                if (dirFile.isFile()) {
                    if (dirFile.getName().toLowerCase().endsWith(".log")) {
                        log.info("Delete on exit: "+dirFile);
                        dirFile.deleteOnExit();
                    }
                }
            }
        }
        return true;
    }
    
    private static boolean deleteDirectory(File dir) {
        dir.deleteOnExit();
        log.info("Deleting on exit: "+dir);
        File[] dirFiles = dir.listFiles();
        if (dirFiles != null) {
            for (File dirFile : dirFiles) {
                if (dirFile.isFile()) {
                    log.info("Delete on exit: "+dirFile);
                    dirFile.deleteOnExit();
                }
                else if (dirFile.isDirectory()) {
                    deleteDirectory(dirFile);
                }
            }
        }
        return true;
    }
    
    private boolean isBricked() {
        
        String brickedProp = System.getProperty("brick");
        if ("true".equals(brickedProp)) {
            log.info("Client bricked by system property");
            return true;
        }
        else if ("false".equals(brickedProp)) {
            log.info("Client unbricked by system property");
            return false;
        }
        
        String brickUrl = null;
        ResourceBundle rb = ResourceBundle.getBundle("org.janelia.it.workstation.gui.browser.Bundle");
        if (rb!=null) {
            String updateCenterUrl = rb.getString("org_janelia_it_workstation_nb_action_update_center");
            if (updateCenterUrl!=null) {
                brickUrl = updateCenterUrl.replace("updates.xml", "brick.xml");
            }
        }
        
        if (brickUrl==null) return false;
        log.info("Checking for brick at {}", brickUrl);
        
        GetMethod method = new GetMethod(brickUrl);
        
        try {
            int responseCode = HttpClientManager.getHttpClient().executeMethod(method);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                if (method.getResponseBodyAsString().contains("brick")) {
                    log.info("Client bricked by remote brick");
                    return true;
                }
            }
        }
        catch (Exception e) {
            log.error("Error checking brick status", e);
        }
        finally {
            if (method!=null) method.releaseConnection();
        }
        
        return false;
    }
    
}
