package org.springframework.datastore.graph.neo4j.fieldaccess;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.datastore.graph.api.*;
import org.springframework.datastore.graph.neo4j.support.GraphDatabaseContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DelegatingFieldAccessorFactory<T> implements FieldAccessorFactory<T> {
    private final static Log log= LogFactory.getLog(DelegatingFieldAccessorFactory.class);
    private final GraphDatabaseContext graphDatabaseContext;

    public DelegatingFieldAccessorFactory(GraphDatabaseContext graphDatabaseContext) {
        this.graphDatabaseContext = graphDatabaseContext;
    }

    @Override
    public boolean accept(Field f) {
        return isSingleRelationshipField(f) || isOneToNRelationshipEntityField(f) || isReadOnlyOneToNRelationshipField(f) || isOneToNRelationshipField(f);
    }

    final Collection<FieldAccessorFactory<?>> fieldAccessorFactories = Arrays.<FieldAccessorFactory<?>>asList(
            IdFieldAccessor.factory(),
            TransientFieldAccessor.factory(),
            NodePropertyFieldAccessor.factory(),
            ConvertingNodePropertyFieldAccessor.factory(),
            SingleRelationshipFieldAccessor.factory(),
            OneToNRelationshipFieldAccessor.factory(),
            ReadOnlyOneToNRelationshipFieldAccessor.factory(),
            OneToNRelationshipEntityFieldAccessor.factory()
            );
    final Collection<FieldAccessorListenerFactory<?>> fieldAccessorListenerFactories = Arrays.<FieldAccessorListenerFactory<?>>asList(
            IndexingNodePropertyFieldAccessorListener.factory()
            );

    public FieldAccessor forField(Field field) {
        if (isAspectjField(field)) return null;
	    for (FieldAccessorFactory<?> fieldAccessorFactory : fieldAccessorFactories) {
		    if (fieldAccessorFactory.accept(field)) {
			    if (log.isInfoEnabled()) log.info("Factory " + fieldAccessorFactory + " used for field: " + field);
			    return fieldAccessorFactory.forField(field);
		    }
	    }
        throw new RuntimeException("No FieldAccessor configured for field: " + field);
	    //log.warn("No FieldAccessor configured for field: " + field);
        //return null;
	}

    private boolean isAspectjField(Field field) {
        return field.getName().startsWith("ajc");
    }


    public static boolean isRelationshipField(Field f) {
		return isSingleRelationshipField(f) 
			|| isOneToNRelationshipField(f)
			|| isOneToNRelationshipEntityField(f)
			|| isReadOnlyOneToNRelationshipField(f);
	}

	private static boolean isSingleRelationshipField(Field f) {
		return NodeBacked.class.isAssignableFrom(f.getType());
	}
	
	private static boolean isOneToNRelationshipField(Field f) {
		if (!Collection.class.isAssignableFrom(f.getType())) return false;
		GraphEntityRelationship relAnnotation = f.getAnnotation(GraphEntityRelationship.class);
		return relAnnotation != null &&  NodeBacked.class.isAssignableFrom(relAnnotation.elementClass()) && !relAnnotation.elementClass().equals(NodeBacked.class);
	}

	private static boolean isReadOnlyOneToNRelationshipField(Field f) {
		GraphEntityRelationship relAnnotation = f.getAnnotation(GraphEntityRelationship.class);
		return Iterable.class.equals(f.getType()) 
			&& relAnnotation != null 
			&& !NodeBacked.class.equals(relAnnotation.elementClass());
	}

	private static boolean isOneToNRelationshipEntityField(Field f) {
		GraphEntityRelationshipEntity relEntityAnnotation = f.getAnnotation(GraphEntityRelationshipEntity.class);
		return Iterable.class.isAssignableFrom(f.getType()) 
			&& relEntityAnnotation != null 
			&& !RelationshipBacked.class.equals(relEntityAnnotation.elementClass());
	}

	public static String getNeo4jPropertyName(Field field) {
        final Class<?> entityClass = field.getDeclaringClass();
        if (useShortNames(entityClass)) return field.getName();
        return String.format("%s.%s", entityClass.getSimpleName(), field.getName());
    }

    private static boolean useShortNames(Class<?> entityClass) {
        final GraphEntity graphEntity = entityClass.getAnnotation(GraphEntity.class);
        if (graphEntity!=null) return graphEntity.useShortNames();
        final GraphRelationship graphRelationship = entityClass.getAnnotation(GraphRelationship.class);
        if (graphRelationship!=null) return graphRelationship.useShortNames();
        return false;
    }

    public List<FieldAccessListener<T, ?>> listenersFor(Field field) {
        List<FieldAccessListener<T,?>> result=new ArrayList<FieldAccessListener<T,?>>();
        for (FieldAccessorListenerFactory<?> fieldAccessorListenerFactory : fieldAccessorListenerFactories) {
            if (fieldAccessorListenerFactory.accept(field)) {
                final FieldAccessListener<T, ?> listener = (FieldAccessListener<T, ?>) fieldAccessorListenerFactory.forField(field);
                result.add(listener);
            }
        }
        return result;
    }
}