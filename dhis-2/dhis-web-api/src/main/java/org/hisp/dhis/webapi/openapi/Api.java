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

import static java.util.stream.Collectors.toList;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import org.hisp.dhis.common.IdentifiableObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Captures the result of the controller analysis process in a data model.
 *
 * Simple fields in the model are "immutable" while collections are used
 * "mutable" to aggregate the results during the analysis process.
 *
 * Descriptions that are added later use a {@link Maybe} box, so they can be
 * used "mutable" too.
 *
 * @author Jan Bernitt
 */
@Value
class Api
{
    List<Controller> controllers = new ArrayList<>();

    /**
     * Note that this needs to use the {@link ConcurrentSkipListMap} as most
     * other maps do not allow to be modified from within a callback that itself
     * is adding an entry like {@link Map#computeIfAbsent(Object, Function)}.
     * Here, while one {@link Schema} is resolved more {@link Schema} might be
     * added.
     */
    Map<Class<?>, Schema> schemas = new ConcurrentSkipListMap<>( Comparator.comparing( Class::getName ) );

    Map<String, Tag> tags = new LinkedHashMap<>();

    /**
     * @return all tags used in the {@link Api}
     */
    Set<String> getUsedTags()
    {
        Set<String> used = new TreeSet<>();
        controllers.forEach( controller -> {
            used.addAll( controller.getTags() );
            controller.endpoints.forEach( endpoint -> used.addAll( endpoint.getTags() ) );
        } );
        used.add( "synthetic" );
        return used;
    }

    /**
     * A {@link Ref} is used for all {@link IdentifiableObject} fields and
     * collections within other objects. This reflects the pattern used in DHIS2
     * that UID references to {@link IdentifiableObject}s are expected in this
     * form:
     *
     * <pre>
     *     {"id": "[uid]"}
     * </pre>
     *
     * while in the database model the full object will occur.
     */
    @Value
    static class Ref
    {
    }

    @Value
    static class Unknown
    {

    }

    @Data
    static final class Maybe<T>
    {
        T value;

        boolean isPresent()
        {
            return value != null;
        }

        T orElse( T defaultValue )
        {
            return value != null ? value : defaultValue;
        }
    }

    @Value
    static class Tag
    {

        String name;

        @EqualsAndHashCode.Exclude
        Maybe<String> description = new Maybe<>();

        @EqualsAndHashCode.Exclude
        Maybe<String> externalDocsUrl = new Maybe<>();

        Maybe<String> externalDocsDescription = new Maybe<>();
    }

    @Value
    static class Field
    {
        String name;

        Boolean required;

        Schema type;
    }

    @Value
    @EqualsAndHashCode( onlyExplicitlyIncluded = true )
    static class Controller
    {
        @ToString.Exclude
        Api in;

        @ToString.Exclude
        @EqualsAndHashCode.Include
        Class<?> source;

        @ToString.Exclude
        @EqualsAndHashCode.Include
        Class<?> entityClass;

        String name;

        List<String> paths = new ArrayList<>();

        List<Endpoint> endpoints = new ArrayList<>();

        Set<String> tags = new TreeSet<>();
    }

    @Value
    @EqualsAndHashCode( onlyExplicitlyIncluded = true )
    static class Endpoint
    {
        @ToString.Exclude
        Controller in;

        @ToString.Exclude
        Method source;

        @ToString.Exclude
        Class<?> entityType;

        @EqualsAndHashCode.Include
        String name;

        Maybe<String> description = new Maybe<>();

        Set<String> tags = new TreeSet<>();

        Boolean deprecated;

        @EqualsAndHashCode.Include
        Set<RequestMethod> methods = EnumSet.noneOf( RequestMethod.class );

        @EqualsAndHashCode.Include
        Set<String> paths = new LinkedHashSet<>();

        Maybe<RequestBody> requestBody = new Maybe<>();

        Map<String, Parameter> parameters = new TreeMap<>();

        Map<HttpStatus, Response> responses = new EnumMap<>( HttpStatus.class );

        boolean isSynthetic()
        {
            return source == null;
        }

        boolean isDeprecated()
        {
            return Boolean.TRUE == deprecated;
        }

        String getEntityTypeName()
        {
            return entityType == null ? "?" : entityType.getSimpleName();
        }
    }

    @Value
    static class RequestBody
    {
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        AnnotatedElement source;

        boolean required;

        String description = "dummy";

        Map<MediaType, Schema> consumes = new TreeMap<>();

    }

    @Value
    static class Parameter
    {
        public enum In
        {
            PATH,
            QUERY,
        }

        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        AnnotatedElement source;

        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Class<?> group;

        String name;

        In in;

        boolean required;

        Maybe<String> description = new Maybe<>();

        Schema type;

        /**
         * @return true, if this parameter is one or many in a complex parameter
         *         object, false, if this parameter directly occurred
         *         individually in the endpoint method signature.
         */
        boolean isGrouped()
        {
            return group != null;
        }
    }

    @Value
    static class Response
    {
        HttpStatus status;

        Map<String, Header> headers = new TreeMap<>();

        Maybe<String> description = new Maybe<>();

        Map<MediaType, Schema> content = new TreeMap<>();

        Response add( Set<MediaType> produces, Schema body )
        {
            produces.forEach( mediaType -> content.put( mediaType, body ) );
            return this;
        }
    }

    @Value
    static class Header
    {
        String name;

        String description;

        Schema type;
    }

    @Value
    @AllArgsConstructor
    static class Schema
    {
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Class<?> source;

        /**
         * Empty unless this is a named "record" type that should be referenced
         * as a named schema in the generated OpenAPI document.
         */
        boolean named;

        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Type hint;

        /**
         * Is empty for primitive types
         */
        List<Field> fields = new ArrayList<>();

        public Schema( Class<?> source, Type hint )
        {
            this( source, Api.isNamed( source ), hint );
        }

        List<String> getRequiredFields()
        {
            return getFields().stream()
                .filter( f -> Boolean.TRUE.equals( f.getRequired() ) )
                .map( Api.Field::getName )
                .collect( toList() );
        }

        Api.Schema add( Field f )
        {
            fields.add( f );
            return this;
        }
    }

    public static final Schema STRING = new Schema( String.class, null );

    public static Schema ref( Class<?> to )
    {
        return new Schema( Ref.class, false, to );
    }

    public static Schema refs( Class<?> to )
    {
        return new Schema( Ref[].class, to );
    }

    public static Schema unknown( Type hint )
    {
        return new Schema( Unknown.class, false, hint );
    }

    static boolean isNamed( Class<?> source )
    {
        String name = source.getName();
        return !source.isPrimitive()
            && !source.isEnum()
            && !source.isArray()
            && !name.startsWith( "java.lang" )
            && !name.startsWith( "java.util" );
    }
}
