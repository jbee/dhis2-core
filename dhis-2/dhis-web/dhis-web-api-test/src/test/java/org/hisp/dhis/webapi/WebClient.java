/*
 * Copyright (c) 2004-2021, University of Oslo
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
package org.hisp.dhis.webapi;

import static org.hisp.dhis.webapi.documentation.common.TestUtils.APPLICATION_JSON_UTF8;
import static org.hisp.dhis.webapi.utils.WebClientUtils.assertSeries;
import static org.hisp.dhis.webapi.utils.WebClientUtils.assertStatus;
import static org.hisp.dhis.webapi.utils.WebClientUtils.failOnException;
import static org.hisp.dhis.webapi.utils.WebClientUtils.plainTextOrJson;
import static org.hisp.dhis.webapi.utils.WebClientUtils.startWithMediaType;
import static org.hisp.dhis.webapi.utils.WebClientUtils.substitutePlaceholders;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hisp.dhis.webapi.json.JsonResponse;
import org.hisp.dhis.webapi.json.domain.JsonError;
import org.hisp.dhis.webapi.utils.ContextUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.shaded.com.google.common.io.ByteStreams;

/**
 * The purpose of this interface is to allow mixin style addition of the
 * convenience web API by implementing this interface's essential method
 * {@link #webRequest(MockHttpServletRequestBuilder)}.
 *
 * @author Jan Bernitt
 */
@FunctionalInterface
public interface WebClient
{

    HttpResponse webRequest( MockHttpServletRequestBuilder request );

    default HttpResponse GET( String url, Object... args )
    {
        return webRequest( get( substitutePlaceholders( url, args ) ), "" );
    }

    default HttpResponse POST( String url, Object... args )
    {
        return POST( substitutePlaceholders( url, args ), "" );
    }

    default HttpResponse POST( String url, String body )
    {
        return webRequest( post( url ), body );
    }

    default HttpResponse PATCH( String url, Object... args )
    {
        return PATCH( substitutePlaceholders( url, args ), "" );
    }

    default HttpResponse PATCH( String url, String body )
    {
        return webRequest( patch( url ), body );
    }

    default HttpResponse PUT( String url, Object... args )
    {
        return PUT( substitutePlaceholders( url, args ), "" );
    }

    default HttpResponse PUT( String url, String body )
    {
        return webRequest( put( url ), body );
    }

    default HttpResponse DELETE( String url, Object... args )
    {
        return DELETE( substitutePlaceholders( url, args ), "" );
    }

    default HttpResponse DELETE( String url, String body )
    {
        return webRequest( delete( url ), body );
    }

    default HttpResponse webRequest( MockHttpServletRequestBuilder request, String body )
    {
        if ( body != null && body.endsWith( ".json" ) )
        {
            return failOnException( () -> webRequest( request
                .contentType( APPLICATION_JSON_UTF8 )
                .content( ByteStreams.toByteArray( new ClassPathResource( body ).getInputStream() ) ) ) );
        }
        if ( startWithMediaType( body ) )
        {
            return webRequest( request
                .contentType( body.substring( 0, body.indexOf( ':' ) ) )
                .content( body.substring( body.indexOf( ':' ) + 1 ) ) );
        }
        return body == null || body.isEmpty()
            ? webRequest( request )
            : webRequest( request
                .contentType( APPLICATION_JSON )
                .content( plainTextOrJson( body ) ) );
    }

    default <T> T run( Function<WebClient, ? extends WebSnippet<T>> snippet )
    {
        return snippet.apply( this ).run();
    }

    default <A, B> B run( BiFunction<WebClient, A, ? extends WebSnippet<B>> snippet, A argument )
    {
        return snippet.apply( this, argument ).run();
    }

    final class HttpResponse
    {
        private final MockHttpServletResponse response;

        public HttpResponse( MockHttpServletResponse response )
        {
            this.response = response;
        }

        public HttpStatus status()
        {
            return HttpStatus.resolve( response.getStatus() );
        }

        public HttpStatus.Series series()
        {
            return status().series();
        }

        public boolean success()
        {
            return series() == Series.SUCCESSFUL;
        }

        public JsonResponse content()
        {
            return content( Series.SUCCESSFUL );
        }

        public JsonResponse content( Series expected )
        {
            assertSeries( expected, this );
            return contentUnchecked();
        }

        public JsonResponse content( HttpStatus expected )
        {
            assertStatus( expected, this );
            return contentUnchecked();
        }

        public JsonError error()
        {
            assertTrue( "not a client or server error", series().value() >= 4 );
            return contentUnchecked().as( JsonError.class );
        }

        public JsonError error( HttpStatus expected )
        {
            assertStatus( expected, this );
            return contentUnchecked().as( JsonError.class );
        }

        public JsonError error( Series expected )
        {
            // OBS! cannot use assertSeries as it uses this method
            assertEquals( expected, series() );
            return contentUnchecked().as( JsonError.class );
        }

        public JsonResponse contentUnchecked()
        {
            return failOnException( () -> new JsonResponse( response.getContentAsString( StandardCharsets.UTF_8 ) ) );
        }

        public String location()
        {
            return header( ContextUtils.HEADER_LOCATION );
        }

        public String header( String name )
        {
            return response.getHeader( name );
        }
    }

}
