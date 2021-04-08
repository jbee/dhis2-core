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
package org.hisp.dhis.gist;

import java.util.List;
import java.util.Map;

/**
 * Gives convenient access to (potentially large) member collections of an owner
 * object.
 *
 * The preferred method should be to {@link #gist(GistQuery)} which returns a
 * raw data row for each match only containing the values of the
 * {@link GistQuery#getFields()} in form of an {@link Object[]}.
 *
 * @author Jan Bernitt
 */
public interface GistService
{
    /**
     * Before running {@link #gist(GistQuery)} a {@link GistQuery} should be
     * planned. This gives the service a change to modify the query with
     * defaults and auto-corrections.
     *
     * The result is exposed again so that the caller can use the modified
     * {@link GistQuery} in subsequent processing steps like serialising the
     * result based on the {@link GistQuery#getFields()}.
     *
     * @param query A {@link GistQuery} as build by the caller
     * @return the rectified {@link GistQuery} that should be used to query
     *         results. This might be the same instance as the provided one or a
     *         modified "copy".
     */
    GistQuery plan( GistQuery query );

    List<?> gist( GistQuery query );

    GistPager pager( GistQuery query, List<?> rows, Map<String, String[]> params );
}
