package org.hisp.dhis;

/*
 * Copyright (c) 2004-2020, University of Oslo
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

import org.hisp.dhis.dbms.DbmsManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/*
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = { IntegrationTestConfig.class } )
@Category( IntegrationTest.class )
@ActiveProfiles( profiles = { "test-postgres" } )
@Transactional
public abstract class IntegrationTestBase extends DhisConvenienceTest implements ApplicationContextAware
{
    @Autowired
    protected DbmsManager dbmsManager;

    private static JdbcTemplate jdbcTemplate;

    /*
    "Special" setter to allow setting JdbcTemplate as static field
     */
    @Autowired
    public void setJdbcTemplate( JdbcTemplate jdbcTemplate )
    {
        IntegrationTestBase.jdbcTemplate = jdbcTemplate;
    }

    /*
        Flag that determines if the IntegrationTestData annotation has
        been running the database init script. We only want to run
        the init script once per unit test
     */
    public static boolean dataInit = false;

    protected ApplicationContext webApplicationContext;

    @Before
    public void before()
        throws Exception
    {
        executeStartupRoutines();

        IntegrationTestData annotation = this.getClass().getAnnotation( IntegrationTestData.class );

        if ( annotation != null && !dataInit )
        {
            ScriptUtils.executeSqlScript( jdbcTemplate.getDataSource().getConnection(),
                new EncodedResource( new ClassPathResource( annotation.path() ), StandardCharsets.UTF_8 ) );
            // only executes once per Unit Test
            dataInit = true;
        }

        // method that can be overridden by subclasses
        setUpTest();
    }

    @AfterClass
    public static void afterClass()
    {
        if ( dataInit ) // only truncate tables if IntegrationTestData is used
        {
            // truncate all tables
            String truncateAll = "DO $$ DECLARE\n" +
                "  r RECORD;\n" +
                "BEGIN\n" +
                "  FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema()) LOOP\n" +
                "    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';\n" +
                "  END LOOP;\n" +
                "END $$;";

            jdbcTemplate.execute( truncateAll );
        }
        // reset data init state
        dataInit = false;
    }

    @After
    public void after()
        throws Exception
    {
        tearDownTest();

        if ( emptyDatabaseAfterTest() )
        {
            dbmsManager.emptyDatabase();
        }
    }

    private void executeStartupRoutines()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        String id = "org.hisp.dhis.system.startup.StartupRoutineExecutor";

        if ( webApplicationContext.containsBean( id ) )
        {
            Object object = webApplicationContext.getBean( id );
            Method method = object.getClass().getMethod( "executeForTesting" );
            method.invoke( object );
        }
    }

    @Override
    public void setApplicationContext( ApplicationContext applicationContext )
        throws BeansException
    {
        this.webApplicationContext = applicationContext;
    }

    public abstract boolean emptyDatabaseAfterTest();

    /**
     * Method to override.
     */
    protected void setUpTest()
        throws Exception
    {
    }

    /**
     * Method to override.
     */
    protected void tearDownTest()
        throws Exception
    {
    }
}
