package org.janelia.it.workstation.gui.browser.api;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.Reference;
import org.janelia.it.jacs.model.domain.Subject;
import org.janelia.it.jacs.model.domain.enums.FileType;
import org.janelia.it.jacs.model.domain.interfaces.HasFilepath;
import org.janelia.it.jacs.model.domain.interfaces.HasFiles;
import org.janelia.it.jacs.model.domain.support.MongoUtils;
import org.janelia.it.jacs.model.domain.support.SearchAttribute;
import org.janelia.it.jacs.model.domain.workspace.TreeNode;
import org.janelia.it.jacs.model.util.ReflectionHelper;
import org.janelia.it.workstation.gui.browser.model.DomainObjectId;
import org.janelia.it.workstation.gui.browser.model.DomainObjectAttribute;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility method for dealing with the Domain model.
 * 
 * TODO: move this to the shared or model module.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class DomainUtils {

    private static final Logger log = LoggerFactory.getLogger(DomainUtils.class);
    
    public static String identify(DomainObject domainObject) {
        return "("+(domainObject==null?"null entity":domainObject.getName())+", @"+System.identityHashCode(domainObject)+")";
    }
    
    public static String getFilepath(HasFilepath hasFilepath) {
        return hasFilepath.getFilepath();
    }
    
    /**
     * @deprecated use the version with FileType instead of this weakly-typed String version
     */
    public static String getFilepath(HasFiles hasFiles, String role) {
        return getFilepath(hasFiles, FileType.valueOf(role));
    }
    
    public static String getFilepath(HasFiles hasFiles, FileType fileType) {
        
        Map<FileType,String> files = hasFiles.getFiles();
        if (files==null) return null;
        String filepath = files.get(fileType);
        if (filepath==null) return null;

        if (filepath.startsWith("/")) {
            // Already an absolute path, don't need to add prefix
            return filepath;
        }
        
        StringBuilder urlSb = new StringBuilder();

        // Add prefix
        if (hasFiles instanceof HasFilepath) {
            String rootPath = ((HasFilepath)hasFiles).getFilepath();
            if (rootPath!=null) {
                urlSb.append(rootPath);
                if (!rootPath.endsWith("/")) urlSb.append("/");
            }
        }
        
        // Add relative path
        urlSb.append(filepath);
        
        return urlSb.length()>0 ? urlSb.toString() : null;
    }

    /**
     * Return true if the given tree node has the specified domain object as a child. 
     * @param treeNode
     * @param domainObject
     * @return
     */
    public static boolean hasChild(TreeNode treeNode, DomainObject domainObject) {
        if (treeNode.hasChildren()) {
            for(Iterator<Reference> i = treeNode.getChildren().iterator(); i.hasNext(); ) {
                Reference iref = i.next();
                if (iref.getTargetId().equals(domainObject.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the collection is null or empty. 
     * @param collection
     * @return
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection==null || collection.isEmpty();
    }
    
    public static String unCamelCase(String s) {
        return s.replaceAll("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})", " ");
    }
    
    public static boolean hasWriteAccess(DomainObject domainObject) {
        return domainObject.getWriters().contains(SessionMgr.getSubjectKey());
    }
    
    public static boolean isOwner(DomainObject domainObject) {
        return domainObject.getOwnerKey().equals(SessionMgr.getSubjectKey());
    }

    public static boolean isVirtual(DomainObject domainObject) {
        // TODO: implement this
        return false;
    }
        
    public static List<DomainObjectAttribute> getAttributes(DomainObject domainObject) {

        List<DomainObjectAttribute> attrs = new ArrayList<>();
        Class<?> clazz = domainObject.getClass();
        
        for (Field field : ReflectionUtils.getAllFields(clazz)) {
            SearchAttribute searchAttributeAnnot = field.getAnnotation(SearchAttribute.class);
            if (searchAttributeAnnot!=null) {
                try {
                    Method getter = ReflectionHelper.getGetter(clazz, field.getName());
                    DomainObjectAttribute attr = new DomainObjectAttribute(searchAttributeAnnot.key(), searchAttributeAnnot.label(), searchAttributeAnnot.facet(), searchAttributeAnnot.display(), getter);
                    attrs.add(attr);
                }
                catch (Exception e) {
                    log.warn("Error getting field " + field.getName() + " on object " + domainObject, e);
                }
            }
        }

        for (Method method : clazz.getMethods()) {
            SearchAttribute searchAttributeAnnot = method.getAnnotation(SearchAttribute.class);
            if (searchAttributeAnnot!=null) {
                DomainObjectAttribute attr = new DomainObjectAttribute(searchAttributeAnnot.key(), searchAttributeAnnot.label(), searchAttributeAnnot.facet(), searchAttributeAnnot.display(), method);
                attrs.add(attr);
            }
        }

        return attrs;
    }
    
    /**
     * Sort a list of subjects in this order: 
     * groups then users, alphabetical by full name, alphabetical by name. 
     * @param subjects
     */
    public static void sortSubjects(List<Subject> subjects) {
        Collections.sort(subjects, new Comparator<Subject>() {
            @Override
            public int compare(Subject o1, Subject o2) {
                ComparisonChain chain = ComparisonChain.start()
                        .compare(o1.getClass().getName(), o2.getClass().getName(), Ordering.natural())
                        .compare(o1.getFullName(), o2.getFullName(), Ordering.natural().nullsLast())
                        .compare(o1.getName(), o2.getName(), Ordering.natural().nullsFirst());
                return chain.result();
            }
        });
    }

    public static List<DomainObjectId> getDomainObjectIdList(Collection<DomainObject> objects) {
        List<DomainObjectId> list = new ArrayList<>();
        for(DomainObject domainObject : objects) {
            if (domainObject!=null) {
                list.add(DomainObjectId.createFor(domainObject));
            }
        }
        return list;
    }

    public static List<Long> getIdList(Collection<DomainObject> objects) {
        List<Long> list = new ArrayList<>();
        for(DomainObject domainObject : objects) {
            if (domainObject!=null) {
                list.add(domainObject.getId());
            }
        }
        return list;
    }

    public static Map<DomainObjectId, DomainObject> getMapByDomainObjectId(Collection<DomainObject> objects) {
        Map<DomainObjectId, DomainObject> objectMap = new HashMap<>();
        for (DomainObject domainObject : objects) {
            if (domainObject != null) {
                objectMap.put(DomainObjectId.createFor(domainObject), domainObject);
            }
        }
        return objectMap;
    }
    
    public static Map<Long, DomainObject> getMapById(Collection<DomainObject> objects) {
        Map<Long, DomainObject> objectMap = new HashMap<>();
        for (DomainObject domainObject : objects) {
            if (domainObject != null) {
                objectMap.put(domainObject.getId(), domainObject);
            }
        }
        return objectMap;
    }
    
    public static Collection<Reference> getReferences(Collection<DomainObject> domainObjects) {
        Collection<Reference> refs = new ArrayList<>();
        for(DomainObject obj : domainObjects) {
            Reference ref = new Reference();
            ref.setTargetId(obj.getId());
            ref.setTargetType(MongoUtils.getCollectionName(obj));
            refs.add(ref);
        }
        return refs;
    }
    
    public static DomainObjectId getIdForReference(Reference ref) {
        Class<? extends DomainObject> clazz = MongoUtils.getObjectClass(ref.getTargetType());
        if (clazz==null) {
            log.warn("Cannot generate DomainObjectId for unrecognized target type: "+ref.getTargetType());
            return null;
        }
        return new DomainObjectId(clazz.getName(), ref.getTargetId());
    }
    
    public static <T> Collection<T> getCollectionOfOne(T object) {
        List<T> list = new ArrayList<>();
        list.add(object);
        return list;
    }
}
