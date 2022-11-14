/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi.openapi;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toSet;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;

import org.hisp.dhis.common.EntityType;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.period.Period;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Given a set of controller {@link Class}es this creates a {@link Api} model
 * that describes all relevant {@link Api.Endpoint}s and {@link Api.Schema}s.
 *
 * @author Jan Bernitt
 */
@NoArgsConstructor( access = AccessLevel.PRIVATE )
final class ApiAnalyser
{
    /**
     * A mapping annotation might have an empty array for the paths which is
     * identical to just the root path o the controller. This array is used to
     * reflect the presence of this root path as the path mapped.
     */
    private static final String[] ROOT_PATH = { "" };

    /**
     * Create an {@link Api} model from controller {@link Class}.
     *
     * The included classes can be filtered based on REST API resource path or
     * {@link OpenApi.Tags} present on the controller class level. Method level
     * path and tags will not be considered for this filter.
     *
     * @param controllers all potential controllers
     * @param paths filter based on resource path (empty includes all)
     * @param tags filter based on tags (empty includes all)
     * @return the {@link Api} for all controllers matching both of the filters
     */
    public static Api describeApi( List<Class<?>> controllers, Set<String> paths, Set<String> tags )
    {
        Api api = new Api();
        Stream<Class<?>> scope = controllers.stream().filter( ApiAnalyser::isControllerType );
        if ( paths != null && !paths.isEmpty() )
        {
            scope = scope.filter( c -> isRoot( c, paths ) );
        }
        if ( tags != null && !tags.isEmpty() )
        {
            scope = scope.filter( c -> c.isAnnotationPresent( OpenApi.Tags.class )
                && Stream.of( c.getAnnotation( OpenApi.Tags.class ).value() ).anyMatch( tags::contains ) );
        }
        scope.forEach( source -> api.getControllers().add( describeController( api, source ) ) );
        return api;
    }

    private static Api.Controller describeController( Api api, Class<?> source )
    {
        String name = getAnnotated( source, RequestMapping.class, RequestMapping::name, n -> !n.isEmpty(),
            () -> source.getSimpleName().replace( "Controller", "" ) );
        Class<?> entityClass = source.isAnnotationPresent( EntityType.class )
            ? source.getAnnotation( EntityType.class ).value()
            : null;
        if ( entityClass == EntityType.class )
        {
            entityClass = (Class<?>) (((ParameterizedType) source.getGenericSuperclass()).getActualTypeArguments()[0]);
        }
        Api.Controller controller = new Api.Controller( api, source, entityClass, name, Descriptions.of( source ) );
        whenAnnotated( source, RequestMapping.class, a -> controller.getPaths().addAll( List.of( a.value() ) ) );
        whenAnnotated( source, OpenApi.Tags.class, a -> controller.getTags().addAll( List.of( a.value() ) ) );

        stream( source.getMethods() )
            .map( ApiAnalyser::getMapping )
            .filter( Objects::nonNull )
            .map( mapping -> describeEndpoint( controller, mapping ) )
            .forEach( endpoint -> controller.getEndpoints().add( endpoint ) );

        return controller;
    }

    private static Api.Endpoint describeEndpoint( Api.Controller controller, Mapping mapping )
    {
        Method source = mapping.getSource();
        String name = mapping.getName().isEmpty() ? source.getName() : mapping.getName();
        Class<?> entityClass = getAnnotated( source, EntityType.class, EntityType::value, c -> c != EntityType.class,
            controller::getEntityClass );

        // request media types
        Set<MediaType> consumes = stream( mapping.getConsumes() ).map( MediaType::parseMediaType ).collect( toSet() );
        if ( consumes.isEmpty() )
        {
            consumes.add( MediaType.APPLICATION_JSON ); // assume JSON if
                                                        // nothing is set
                                                        // explicitly
        }

        Api.Endpoint endpoint = new Api.Endpoint( controller, source, entityClass, name,
            ConsistentAnnotatedElement.of( source ).isAnnotationPresent( Deprecated.class ) ? Boolean.TRUE : null );
        endpoint.getDescription().setValue( controller.getDescriptions().get( source.getName() + ".description",
            desc -> desc.replace( "{entityType}", endpoint.getEntityTypeName() ) ) );

        whenAnnotated( source, OpenApi.Tags.class, a -> endpoint.getTags().addAll( List.of( a.value() ) ) );
        Stream.of( mapping.getPath() )
            .map( path -> path.endsWith( "/" ) ? path.substring( 0, path.length() - 1 ) : path )
            .forEach( path -> endpoint.getPaths().add( path ) );
        endpoint.getMethods().addAll( Set.of( mapping.method ) );

        // request:
        describeParameters( endpoint, consumes );

        // response:
        endpoint.getResponses().putAll( describeResponses( endpoint, mapping, consumes ) );

        return endpoint;
    }

    private static Map<HttpStatus, Api.Response> describeResponses( Api.Endpoint endpoint, Mapping mapping,
        Set<MediaType> consumes )
    {
        Method source = mapping.getSource();
        Set<MediaType> produces = stream( mapping.getProduces() )
            .map( MediaType::parseMediaType )
            .collect( toSet() );
        if ( produces.isEmpty() )
        {
            // either make symmetric or assume JSON as standard
            if ( consumes.contains( MediaType.APPLICATION_JSON ) || consumes.contains( MediaType.APPLICATION_XML ) )
            {
                produces.addAll( consumes ); // make symmetric
            }
            else
            {
                produces.add( MediaType.APPLICATION_JSON );
            }
        }

        HttpStatus signatureStatus = getAnnotated( source, ResponseStatus.class,
            a -> firstNonEqual( HttpStatus.INTERNAL_SERVER_ERROR, a.value(), a.code() ),
            s -> s != HttpStatus.INTERNAL_SERVER_ERROR, () -> HttpStatus.OK );

        Map<HttpStatus, Api.Response> res = new LinkedHashMap<>();
        // response(s) declared via annotation(s)
        getAnnotations( source, OpenApi.Response.class )
            .forEach( a -> (a.status().length == 0 ? List.of( signatureStatus ) : List.of( a.status() ))
                .forEach( status -> res.put( status,
                    describeResponse( endpoint, produces, status, getSubstitutedType( endpoint, a.value() ) ) ) ) );
        // response from method signature
        res.computeIfAbsent( signatureStatus,
            status -> describeResponse( endpoint, produces, status, source.getGenericReturnType() ) );
        return res;
    }

    private static Api.Response describeResponse( Api.Endpoint endpoint, Set<MediaType> produces,
        HttpStatus status, Type type )
    {
        Api.Response response = new Api.Response( status );
        if ( type != void.class && type != Void.class )
        {
            response.add( produces, describeOutputSchema( endpoint, type ) );
        }
        String descKey = endpoint.getName() + ".response." + status.value() + ".description";
        response.getDescription().setValue( endpoint.getIn().getDescriptions().get( descKey,
            desc -> desc.replace( "{entityType}", endpoint.getEntityTypeName() ) ) );
        return response;

    }

    private static void describeParameters( Api.Endpoint endpoint, Set<MediaType> consumes )
    {
        Method source = endpoint.getSource();
        // request parameter(s) declared via annotation(s)
        getAnnotations( source, OpenApi.Param.class ).forEach( p -> describeParam( endpoint, p, consumes ) );
        getAnnotations( source, OpenApi.Params.class ).forEach( p -> describeParams( endpoint, p ) );

        // request parameters from method signature
        for ( Parameter p : source.getParameters() )
        {
            if ( p.isAnnotationPresent( OpenApi.Ignore.class ) )
            {
                continue;
            }
            if ( p.isAnnotationPresent( PathVariable.class ) )
            {
                PathVariable a = p.getAnnotation( PathVariable.class );
                String name = firstNonEmpty( a.name(), a.value(), p.getName() );
                endpoint.getParameters().computeIfAbsent( name,
                    key -> new Api.Parameter( p, key, Api.Parameter.In.PATH, a.required(),
                        describeInputSchema( endpoint, p.getParameterizedType() ) ) );
            }
            if ( p.isAnnotationPresent( RequestParam.class ) && p.getType() != Map.class )
            {
                RequestParam a = p.getAnnotation( RequestParam.class );
                boolean required = a.required()
                    && a.defaultValue().equals( "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n" );
                String name = firstNonEmpty( a.name(), a.value(), p.getName() );
                endpoint.getParameters().computeIfAbsent( name,
                    key -> new Api.Parameter( p, key, Api.Parameter.In.QUERY, required,
                        describeInputSchema( endpoint, p.getParameterizedType() ) ) );
            }
            else if ( p.isAnnotationPresent( RequestBody.class ) )
            {
                RequestBody a = p.getAnnotation( RequestBody.class );
                Api.RequestBody requestBody = endpoint.getRequestBody().isPresent()
                    ? endpoint.getRequestBody().getValue()
                    : new Api.RequestBody( p, a.required() );
                Api.Schema type = describeInputSchema( endpoint, p.getParameterizedType() );
                consumes.forEach( mediaType -> requestBody.getConsumes().putIfAbsent( mediaType, type ) );
            }
            else if ( isParams( p ) )
            {
                boolean requireJsonProperty = stream( p.getType().getMethods() )
                    .anyMatch( m -> m.isAnnotationPresent( JsonProperty.class ) );
                for ( Method m : p.getType().getMethods() )
                {
                    if ( isEndpointParameter( m, 1, List.of( "set" ), requireJsonProperty ) )
                    {
                        Api.Parameter parameter = describeParameter( endpoint, m );
                        endpoint.getParameters().putIfAbsent( parameter.getName(), parameter );
                    }
                }
            }
        }
    }

    private static void describeParam( Api.Endpoint endpoint, OpenApi.Param param, Set<MediaType> consumes )
    {
        String name = param.name();
        Api.Schema type = describeInputSchema( endpoint, getSubstitutedType( endpoint, param.value() ) );
        boolean required = param.required();
        if ( !name.isEmpty() )
        {
            endpoint.getParameters().put( name,
                new Api.Parameter( endpoint.getSource(), name, Api.Parameter.In.QUERY, required, type ) );
        }
        else
        {
            Api.RequestBody requestBody = new Api.RequestBody( endpoint.getSource(), required );
            consumes.forEach( mediaType -> requestBody.getConsumes().put( mediaType, type ) );
            endpoint.getRequestBody().setValue( requestBody );
        }
    }

    private static void describeParams( Api.Endpoint endpoint, OpenApi.Params params )
    {
        Class<?> value = params.value();
        boolean requireJsonProperty = stream( value.getMethods() )
            .anyMatch( m -> m.isAnnotationPresent( JsonProperty.class ) );
        stream( value.getMethods() )
            .filter( m -> isEndpointParameter( m, 0, List.of( "has", "is", "get" ), requireJsonProperty ) )
            .map( m -> describeParameter( endpoint, m ) )
            .forEach( p -> endpoint.getParameters().putIfAbsent( p.getName(), p ) );
    }

    private static Api.Parameter describeParameter( Api.Endpoint endpoint, Method source )
    {
        String name = getName( source );
        Type type = source.getParameterCount() == 0
            ? source.getGenericReturnType()
            : source.getGenericParameterTypes()[0];
        return new Api.Parameter( source, name, Api.Parameter.In.QUERY, false,
            describeInputSchema( endpoint, type ) );
    }

    private static Api.Schema describeInputSchema( Api.Endpoint endpoint, Type s )
    {
        return describeSchema( endpoint, s, false, new IdentityHashMap<>() );
    }

    private static Api.Schema describeOutputSchema( Api.Endpoint endpoint, Type source )
    {
        return describeSchema( endpoint, source, false, new IdentityHashMap<>() );
    }

    /**
     * @return a schema describing a complex "record-like" or "bean" object
     */
    private static Api.Schema describeTypeSchema( Api.Endpoint endpoint, Class<?> type,
        Map<Class<?>, Api.Schema> resolving )
    {
        Api.Schema s = resolving.get( type );
        if ( s != null )
            return s;
        return endpoint.getIn().getIn().getSchemas().computeIfAbsent( type, key -> {
            Api.Schema schema = new Api.Schema( type, null );
            resolving.put( type, schema );
            if ( type.isEnum() || type.isInterface() )
                return schema;
            if ( type.isArray() )
            {
                // eventually this will resolve the simple element type
                describeTypeSchema( endpoint, type.getComponentType(), resolving );
                return schema;
            }
            String moduleName = type.getModule().getName();
            if ( moduleName != null && (moduleName.startsWith( "java." ) || moduleName.startsWith( "jdk." )) )
            {
                // this is a build in type which we assume is a simple type with
                // no fields
                return schema;
            }
            Set<String> fieldsAdded = new HashSet<>();
            for ( Method m : type.getMethods() )
            {
                if ( isSchemaField( m ) && !fieldsAdded.contains( getName( m ) ) )
                {
                    Type t = m.getParameterCount() == 1 ? m.getGenericParameterTypes()[0] : m.getGenericReturnType();
                    Api.Schema ms = describeSchema( endpoint, t, true, resolving );
                    if ( ms != null )
                    {
                        String name = getName( m );
                        fieldsAdded.add( name );
                        schema.getFields().add( new Api.Field( name, ms, getFieldRequired( m ) ) );
                    }
                }
            }
            for ( Field f : type.getDeclaredFields() )
            {
                if ( isSchemaField( f ) && !fieldsAdded.contains( getName( f ) ) )
                {
                    Api.Schema fs = describeSchema( endpoint, f.getGenericType(), true, resolving );
                    if ( fs != null )
                    {
                        String name = getName( f );
                        fieldsAdded.add( name );
                        schema.getFields().add( new Api.Field( name, fs, getFieldRequired( f ) ) );
                    }
                }
            }
            return schema;
        } );
    }

    private static Api.Schema describeSchema( Api.Endpoint endpoint, Type source, boolean useRefs,
        Map<Class<?>, Api.Schema> resolving )
    {
        if ( source instanceof Class<?> )
        {
            Class<?> type = (Class<?>) source;
            if ( useRefs && IdentifiableObject.class.isAssignableFrom( type ) && type != Period.class )
                return Api.ref( type );
            if ( useRefs && IdentifiableObject[].class.isAssignableFrom( type ) && type != Period[].class )
                return Api.refs( type.getComponentType() );
            return describeTypeSchema( endpoint, type, resolving );
        }
        if ( source instanceof ParameterizedType )
        {
            ParameterizedType pt = (ParameterizedType) source;
            Class<?> rawType = (Class<?>) pt.getRawType();
            if ( rawType == Class.class )
                return new Api.Schema( String.class, "", source );
            Type typeArg0 = pt.getActualTypeArguments()[0];
            if ( Collection.class.isAssignableFrom( rawType ) && rawType.isInterface() || rawType == Iterable.class )
            {
                if ( typeArg0 instanceof Class<?> )
                    return describeSchema( endpoint, Array.newInstance( (Class<?>) typeArg0, 0 ).getClass(),
                        useRefs, resolving );
                Api.Schema colSchema = new Api.Schema( Collection.class, source );
                colSchema.getFields()
                    .add( new Api.Field( "", describeSchema( endpoint, typeArg0, useRefs, resolving ), true ) );
                return colSchema;
            }
            if ( Map.class.isAssignableFrom( rawType ) && rawType.isInterface() )
            {
                Api.Schema mapSchema = new Api.Schema( Map.class, source );
                mapSchema.getFields().add( new Api.Field( "key",
                    describeSchema( endpoint, typeArg0, false, resolving ), true ) );
                mapSchema.getFields().add( new Api.Field( "value",
                    describeSchema( endpoint, pt.getActualTypeArguments()[1], useRefs, resolving ), true ) );
                return mapSchema;
            }
            if ( rawType == ResponseEntity.class )
            {
                return describeSchema( endpoint, typeArg0, false, resolving );
            }
            return Api.unknown( source );
        }
        if ( source instanceof WildcardType )
        {
            WildcardType wt = (WildcardType) source;
            if ( wt.getLowerBounds().length == 0
                && Arrays.equals( wt.getUpperBounds(), new Type[] { Object.class } ) )
                return new Api.Schema( Object.class, "", wt );
            // simplification: <? extends X> => <X>
            return describeSchema( endpoint, wt.getUpperBounds()[0], useRefs, resolving );
        }
        return Api.unknown( source );
    }

    private static Boolean getFieldRequired( AnnotatedElement source )
    {
        JsonProperty a = source.getAnnotation( JsonProperty.class );
        if ( a.required() )
            return true;
        if ( !a.defaultValue().isEmpty() )
            return false;
        return null;
    }

    private static String getPropertyName( Method method )
    {
        String name = method.getName();
        String prop = name.substring( name.startsWith( "is" ) ? 2 : 3 );
        return Character.toLowerCase( prop.charAt( 0 ) ) + prop.substring( 1 );
    }

    private static <T extends Member & AnnotatedElement> String getName( T member )
    {
        String name = member instanceof Field ? member.getName() : getPropertyName( (Method) member );
        JsonProperty property = member.getAnnotation( JsonProperty.class );
        String customName = property == null ? "" : property.value();
        return customName.isEmpty() ? name : customName;
    }

    /**
     * @return the type referred to by the type found in an annotation.
     */
    private static Class<?> getSubstitutedType( Api.Endpoint endpoint, Class<?> type )
    {
        if ( type == EntityType.class && endpoint.getEntityType() != null )
        {
            return endpoint.getEntityType();
        }
        if ( type == EntityType[].class && endpoint.getEntityType() != null )
        {
            return Array.newInstance( endpoint.getEntityType(), 0 ).getClass();
        }
        return type;
    }

    private static <T extends AnnotatedElement & Member> boolean isSchemaField( T member )
    {
        return member.isAnnotationPresent( JsonProperty.class ) && !Modifier.isStatic( member.getModifiers() );
    }

    private static boolean isControllerType( Class<?> source )
    {
        return (source.isAnnotationPresent( RestController.class )
            || source.isAnnotationPresent( Controller.class ))
            && !source.isAnnotationPresent( OpenApi.Ignore.class );
    }

    /**
     * @return is this a parameter objects with properties which are parameters?
     */
    private static boolean isParams( Parameter source )
    {
        Class<?> type = source.getType();
        if ( type.isInterface()
            || type.isEnum()
            || IdentifiableObject.class.isAssignableFrom( type )
            || source.getAnnotations().length > 0
            || source.isAnnotationPresent( OpenApi.Ignore.class ) )
            return false;
        return stream( type.getDeclaredConstructors() ).anyMatch( c -> c.getParameterCount() == 0 );
    }

    private static boolean isEndpointParameter( Method source, int parameterCount, List<String> prefixes,
        boolean requireJsonProperty )
    {
        return prefixes.stream().anyMatch( prefix -> source.getName().startsWith( prefix ) )
            && source.getParameterCount() == parameterCount
            && Modifier.isPublic( source.getModifiers() )
            && source.getDeclaringClass() != Object.class
            && (!requireJsonProperty || source.isAnnotationPresent( JsonProperty.class ));
    }

    private static boolean isRoot( Class<?> controller, Set<String> included )
    {
        RequestMapping a = controller.getAnnotation( RequestMapping.class );
        return a != null && stream( firstNonEmpty( a.value(), a.path() ) ).anyMatch( included::contains );
    }

    private static Mapping getMapping( Method source )
    {
        if ( ConsistentAnnotatedElement.of( source ).isAnnotationPresent( OpenApi.Ignore.class ) )
        {
            return null;// ignore this
        }
        if ( source.isAnnotationPresent( RequestMapping.class ) )
        {
            RequestMapping a = source.getAnnotation( RequestMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                a.method(), a.params(), a.headers(), a.consumes(), a.produces() );
        }
        if ( source.isAnnotationPresent( GetMapping.class ) )
        {
            GetMapping a = source.getAnnotation( GetMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                new RequestMethod[] { RequestMethod.GET }, a.params(), a.headers(), a.consumes(), a.produces() );
        }
        if ( source.isAnnotationPresent( PutMapping.class ) )
        {
            PutMapping a = source.getAnnotation( PutMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                new RequestMethod[] { RequestMethod.PUT }, a.params(), a.headers(), a.consumes(), a.produces() );
        }
        if ( source.isAnnotationPresent( PostMapping.class ) )
        {
            PostMapping a = source.getAnnotation( PostMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                new RequestMethod[] { RequestMethod.POST }, a.params(), a.headers(), a.consumes(), a.produces() );
        }
        if ( source.isAnnotationPresent( PatchMapping.class ) )
        {
            PatchMapping a = source.getAnnotation( PatchMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                new RequestMethod[] { RequestMethod.PATCH }, a.params(), a.headers(), a.consumes(), a.produces() );
        }
        if ( source.isAnnotationPresent( DeleteMapping.class ) )
        {
            DeleteMapping a = source.getAnnotation( DeleteMapping.class );
            return new Mapping( source, a.name(), firstNonEmpty( a.value(), a.path(), ROOT_PATH ),
                new RequestMethod[] { RequestMethod.DELETE }, a.params(), a.headers(), a.consumes(), a.produces() );
        }
        return null;
    }

    private static String[] firstNonEmpty( String[] a, String[] b, String[] c )
    {
        return firstNonEmpty( firstNonEmpty( a, b ), c );
    }

    private static String[] firstNonEmpty( String[] a, String[] b )
    {
        return a.length == 0 ? b : a;
    }

    private static String firstNonEmpty( String a, String b )
    {
        return a.length() == 0 ? b : a;
    }

    private static String firstNonEmpty( String a, String b, String c )
    {
        String ab = firstNonEmpty( a, b );
        return ab.length() > 0 ? ab : c;
    }

    @SafeVarargs
    private static <E extends Enum<E>> E firstNonEqual( E to, E... samples )
    {
        return stream( samples ).filter( e -> e != to ).findFirst().orElse( samples[0] );
    }

    @Value
    static class Mapping
    {
        Method source;

        String name;

        String[] path;

        RequestMethod[] method;

        String[] params;

        String[] headers;

        String[] consumes;

        String[] produces;
    }

    /*
     * Helpers for working with annotations
     */

    private static <A extends Annotation> Stream<A> getAnnotations( Method on, Class<A> type )
    {
        return stream( ConsistentAnnotatedElement.of( on ).getAnnotationsByType( type ) );
    }

    private static <A extends Annotation, T extends AnnotatedElement> void whenAnnotated( T on, Class<A> type,
        Consumer<A> whenPresent )
    {
        AnnotatedElement target = ConsistentAnnotatedElement.of( on );
        if ( target.isAnnotationPresent( type ) )
        {
            whenPresent.accept( target.getAnnotation( type ) );
        }
    }

    private static <A extends Annotation, B, T extends AnnotatedElement> B getAnnotated( T on, Class<A> type,
        Function<A, B> whenPresent, Predicate<B> test, Supplier<B> otherwise )
    {
        AnnotatedElement target = ConsistentAnnotatedElement.of( on );
        if ( !target.isAnnotationPresent( type ) )
        {
            return otherwise.get();
        }
        B value = whenPresent.apply( target.getAnnotation( type ) );
        return test.test( value ) ? value : otherwise.get();
    }
}
