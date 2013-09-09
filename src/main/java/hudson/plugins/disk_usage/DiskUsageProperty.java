package hudson.plugins.disk_usage;


import hudson.model.*;
import hudson.Extension;
import hudson.FilePath;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

//(basically nothing to see here)
/**
 * This Property sets DiskUsage action. 
 * 
 * @author dvrzalik
 */
public class DiskUsageProperty extends JobProperty<Job<?, ?>> {

    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        return Collections.emptyList();
    }
    
     private Long diskUsageWithoutBuilds = 0l;
     private Map<String,Map<String,Long>> slaveWorkspacesUsage = new TreeMap<String,Map<String,Long>>();
     
     public void setDiskUsageWithoutBuilds(Long diskUsageWithoutBuilds){
            this.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
        }
        
        public void putSlaveWorkspace(Node node, String path){
            Map<String,Long> workspacesInfo = slaveWorkspacesUsage.get(node.getNodeName());
            if(workspacesInfo==null){
               workspacesInfo = new TreeMap<String,Long>();
            }
            if(!workspacesInfo.containsKey(path))
                workspacesInfo.put(path, 0l);
            slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
        }
        
        public Map<String,Map<String,Long>> getSlaveWorkspaceUsage(){
            return slaveWorkspacesUsage;
        }
        
        public void putSlaveWorkspaceSize(Node node, String path, Long size){
            Map<String,Long> workspacesInfo = slaveWorkspacesUsage.get(node.getNodeName());
            if(workspacesInfo==null)
                workspacesInfo = new TreeMap<String,Long>();
            workspacesInfo.put(path, size);
            slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
        }
        
        public Long getWorkspaceSize(Boolean containdedInWorkspace){
            Long size = 0l;
            for(String nodeName: slaveWorkspacesUsage.keySet()){
                Node node = Jenkins.getInstance().getNode(nodeName);
                String workspacePath = null;
                if(node instanceof Jenkins){
                    workspacePath = Jenkins.getInstance().getRawWorkspaceDir();
                }
                if(node instanceof Slave){
                    workspacePath = ((Slave) node).getRemoteFS();
                }
                if(workspacePath==null)
                    continue;
                Map<String,Long> paths = slaveWorkspacesUsage.get(nodeName);
                for(String path: paths.keySet()){
                    if(containdedInWorkspace.equals(path.startsWith(workspacePath))){
                        size += paths.get(path);
                    }
                }
            }
            return size;
        }
                
        public void checkWorkspaces() {
                List<AbstractBuild> builds = (List<AbstractBuild>) owner.getBuilds();
                    for(AbstractBuild build: builds){
                        if(!build.isBuilding()){
                            Node node = build.getBuiltOn();
                            FilePath path = build.getWorkspace();
                            if(path==null)
                                continue;
                            putSlaveWorkspace(node, path.getRemote());
                        }
                    }
                //only if it is wanted - can cost a quite long time to do it for all
                if(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getCheckWorkspaceOnSlave() && owner instanceof TopLevelItem){
                    for(Node node: Jenkins.getInstance().getNodes()){
                        if(node.toComputer()!=null && node.toComputer().isOnline()){
                            FilePath path =null;
                            try{
                                path = node.getWorkspaceFor((TopLevelItem)owner);                  
                                if(path!=null && path.exists() && (slaveWorkspacesUsage.get(node.getNodeName())==null || !slaveWorkspacesUsage.get(node.getNodeName()).containsKey(path.getRemote()))){
                                    putSlaveWorkspace(node, path.getRemote());
                                }
                            }
                            catch(Exception e){
                                LOGGER.warning("Can not check if file " + path.getRemote() + " exists on node " + node.getNodeName());
                            }
                        }
                    }
                }

        }
        
        public Long getAllWorkspaceSize(){
            Long size = 0l;
            for(String nodeName: slaveWorkspacesUsage.keySet()){
                Map<String,Long> paths = slaveWorkspacesUsage.get(nodeName);
                for(String path: paths.keySet()){
                        size += paths.get(path);
                }
            }
            return size;
        }
        
        public Long getDiskUsageWithoutBuilds(){
            return diskUsageWithoutBuilds;
        }
        
        public Long getAllDiskUsageWithoutBuilds(){
           Long diskUsage = diskUsageWithoutBuilds;
           if(owner instanceof ItemGroup){
                     ItemGroup group = (ItemGroup) owner;
                         diskUsage += getDiskUsageWithoutBuildsAllSubItems(group);
           }
           return diskUsage;
        }
        
        private Long getDiskUsageWithoutBuildsAllSubItems(ItemGroup group){
        Long diskUsage = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               diskUsage += getDiskUsageWithoutBuildsAllSubItems(subGroup);
            }
            if(item instanceof AbstractProject){
                AbstractProject p = (AbstractProject) item;
                DiskUsageProperty property = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                if(property!=null){
                    diskUsage += property.getDiskUsageWithoutBuilds();
                }
            }
        }
        return diskUsage;
    }

    @Extension
    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        public DiskUsageDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }


        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
             return new DiskUsageProperty();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
    
    public static final Logger LOGGER = Logger.getLogger(DiskUsageProperty.class.getName());
}

    
