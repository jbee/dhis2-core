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
package org.hisp.dhis.scheduling;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableCollection;
import static org.hisp.dhis.util.DateUtils.getMediumDateString;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import org.hisp.dhis.cache.Cache;
import org.hisp.dhis.cache.CacheProvider;
import org.hisp.dhis.common.AsyncTaskExecutor;
import org.hisp.dhis.leader.election.LeaderManager;
import org.hisp.dhis.message.MessageService;
import org.hisp.dhis.scheduling.JobProgress.Process;
import org.hisp.dhis.system.notification.Notifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * A {@link SchedulingManager} that runs {@link #schedule(JobConfiguration)} and
 * {@link #executeNow(JobConfiguration)} asynchronously.
 *
 * Whether or not a job can run is solely determined by
 * {@link #isRunning(JobType)} which makes sure only one asynchronous task can
 * run at a time.
 *
 * The {@link DefaultSchedulingManager} manages its private state with the sole
 * goal of being able to cancel asynchronously running tasks.
 *
 * @author Henning Håkonsen (original implementation)
 * @author Jan Bernitt (refactored)
 */
@Slf4j
@Service( "org.hisp.dhis.scheduling.SchedulingManager" )
public class DefaultSchedulingManager extends AbstractSchedulingManager
{
    private static final int DEFAULT_INITIAL_DELAY_S = 10;

    private final Map<JobType, Future<?>> scheduled = new ConcurrentHashMap<>();

    private final Map<JobType, Future<?>> running = new ConcurrentHashMap<>();

    private final TaskScheduler jobScheduler;

    private final AsyncTaskExecutor taskExecutor;

    private final Cache<Deque<Process>> runningJobsInfo;

    private final Cache<Deque<Process>> completedJobsInfo;

    private final Cache<Boolean> cancelRequested;

    public DefaultSchedulingManager( JobService jobService, JobConfigurationService jobConfigurationService,
        MessageService messageService, Notifier notifier,
        LeaderManager leaderManager, @Qualifier( "taskScheduler" ) TaskScheduler jobScheduler,
        AsyncTaskExecutor taskExecutor, CacheProvider cacheProvider )
    {
        super( jobService, jobConfigurationService, messageService, leaderManager, notifier );
        checkNotNull( jobConfigurationService );
        checkNotNull( messageService );
        checkNotNull( leaderManager );
        checkNotNull( jobScheduler );
        checkNotNull( taskExecutor );
        checkNotNull( jobService );

        this.jobScheduler = jobScheduler;
        this.taskExecutor = taskExecutor;
        this.runningJobsInfo = cacheProvider.createRunningJobsInfoCache();
        this.completedJobsInfo = cacheProvider.createCompletedJobsInfoCache();
        this.cancelRequested = cacheProvider.createJobCancelRequestedCache();

        jobScheduler.scheduleWithFixedDelay( this::clusterHeartbeat, Duration.ofSeconds( 30 ) );
    }

    private void clusterHeartbeat()
    {
        for ( Entry<JobType, ControlledJobProgress> info : runningJobProgress.entrySet() )
        {
            String key = info.getKey().name();
            if ( cancelRequested.getIfPresent( key ).isPresent() )
            {
                cancel( info.getKey() );
                cancelRequested.invalidate( key );
            }
            else
            {
                // heart beat update so the entry does not expire
                runningJobsInfo.put( key, info.getValue().getProcesses() );
            }
        }
        // always use most recent local in the cache
        for ( Entry<JobType, ControlledJobProgress> localInfo : completedJobProgress.entrySet() )
        {
            String key = localInfo.getKey().name();
            Optional<Deque<Process>> clusterInfo = completedJobsInfo.getIfPresent( key );
            Date now = new Date();
            if ( clusterInfo.isEmpty()
                || Process.completedTime( clusterInfo.get(), now )
                    .before( Process.completedTime( localInfo.getValue().getProcesses(), now ) ) )
            {
                completedJobsInfo.put( key, localInfo.getValue().getProcesses() );
            }
        }
    }

    @Override
    public Collection<JobType> getRunningTypes()
    {
        EnumSet<JobType> inCluster = EnumSet.copyOf( super.getRunningTypes() );
        runningJobsInfo.keys().forEach( key -> inCluster.add( JobType.valueOf( key ) ) );
        return inCluster;
    }

    @Override
    public Collection<JobType> getCompletedTypes()
    {
        EnumSet<JobType> inCluster = EnumSet.copyOf( super.getCompletedTypes() );
        completedJobsInfo.keys().forEach( key -> inCluster.add( JobType.valueOf( key ) ) );
        return inCluster;
    }

    @Override
    public Collection<Process> getRunningProgress( JobType type )
    {

        Collection<Process> localInfo = super.getRunningProgress( type );
        if ( !localInfo.isEmpty() )
        {
            return localInfo;
        }
        var clusterInfo = runningJobsInfo.getIfPresent( type.name() );
        return clusterInfo.isEmpty() ? emptyList() : unmodifiableCollection( clusterInfo.get() );
    }

    @Override
    public Collection<Process> getCompletedProgress( JobType type )
    {
        Collection<Process> localInfo = super.getCompletedProgress( type );
        var clusterInfo = completedJobsInfo.getIfPresent( type.name() );
        if ( clusterInfo.isEmpty() )
        {
            return localInfo;
        }
        Date now = new Date();
        return localInfo.isEmpty()
            || Process.completedTime( clusterInfo.get(), now ).after( Process.completedTime( localInfo, now ) )
                ? clusterInfo.get()
                : localInfo;
    }

    @Override
    public void cancel( JobType type )
    {
        cancelRequested.put( type.name(), true );
        super.cancel( type );
    }

    @Override
    protected boolean canRunInCluster( JobType type, Deque<Process> initialState )
    {
        return runningJobsInfo.putIfAbsent( type.name(), initialState );
    }

    @Override
    public void schedule( JobConfiguration configuration )
    {
        log.info( String.format( "Scheduling job: %s", configuration ) );

        scheduleTask( configuration, task -> configuration.getJobType().getSchedulingType() == SchedulingType.CRON
            ? scheduleCronBased( configuration, task )
            : scheduleFixedDelayBased( configuration, task ) );

        log.info( String.format( "Scheduled job: %s", configuration ) );
    }

    @Override
    public void scheduleWithStartTime( JobConfiguration configuration, Date startTime )
    {
        scheduleTask( configuration, task -> scheduleTimeBased( startTime, task ) );

        log.info( String.format( "Scheduled job: %s with start time: %s", configuration,
            getMediumDateString( startTime ) ) );
    }

    private void scheduleTask( JobConfiguration configuration, Function<Runnable, Future<?>> scheduler )
    {
        String jobId = configuration.getUid();
        JobType type = configuration.getJobType();
        CompletableFuture<Future<?>> cancellation = new CompletableFuture<>();
        Runnable task = configuration.isInMemoryJob()
            ? () -> execute( configuration )
            : () -> execute( jobId );
        Future<?> cancelable = scheduler.apply( runIfPossible( configuration, cancellation, task ) );
        Future<?> scheduledBefore = scheduled.put( type, cancelable );
        if ( scheduledBefore != null && !scheduledBefore.cancel( true ) )
        {
            cancellation.cancel( false );
        }
        else
        {
            cancellation.complete( cancelable );
        }
    }

    private Future<?> scheduleFixedDelayBased( JobConfiguration configuration, Runnable task )
    {
        return jobScheduler.scheduleWithFixedDelay( task,
            Instant.now().plusSeconds( DEFAULT_INITIAL_DELAY_S ),
            Duration.of( configuration.getDelay(), ChronoUnit.SECONDS ) );
    }

    private Future<?> scheduleCronBased( JobConfiguration configuration, Runnable task )
    {
        return jobScheduler.schedule( task,
            new CronTrigger( configuration.getCronExpression() ) );
    }

    private Future<?> scheduleTimeBased( Date startTime, Runnable task )
    {
        return jobScheduler.schedule( task, startTime );
    }

    @Override
    public void stop( JobConfiguration configuration )
    {
        JobType type = configuration.getJobType();
        if ( type != null && isRunning( type ) )
        {
            stoppedSuccessful( type );
        }
    }

    @Override
    public boolean executeNow( JobConfiguration configuration )
    {
        if ( configuration == null )
        {
            return false;
        }
        JobType type = configuration.getJobType();
        if ( type == null || isRunning( type ) )
        {
            return false;
        }
        log.info( String.format( "Scheduler initiated execution of job: %s", configuration ) );
        CompletableFuture<Future<?>> cancellation = new CompletableFuture<>();
        Runnable task = runIfPossible( configuration, cancellation, () -> execute( configuration ) );
        cancellation.complete( taskExecutor.executeTaskWithCancelation( task ) );
        return true;
    }

    /**
     * Wraps the original task in order to manage cancellation state correctly
     * as part of running the task so that transitions occur when the task
     * actually executes.
     */
    private Runnable runIfPossible( JobConfiguration configuration, Future<Future<?>> cancellation, Runnable task )
    {
        JobType type = configuration.getJobType();
        return () -> {
            Future<?> cancelable = getOrThrow( cancellation );
            if ( cancelable == null )
            {
                log.warn( "Job {} aborted as no cancellation was provided", type );
                return;
            }
            scheduled.remove( type, cancelable );
            if ( type == null || isRunning( type ) && !stoppedSuccessful( type ) )
            {
                return;
            }
            running.put( type, cancelable );
            try
            {
                task.run();
            }
            finally
            {
                JobStatus lastExecutedStatus = configuration.getLastExecutedStatus();
                if ( cancelable.isCancelled()
                    && (lastExecutedStatus == JobStatus.RUNNING || lastExecutedStatus == JobStatus.COMPLETED) )
                {
                    configuration.setLastExecutedStatus( JobStatus.STOPPED );
                }
                running.remove( type, cancelable );
            }
        };
    }

    private Future<?> getOrThrow( Future<Future<?>> cancelProvider )
    {
        try
        {
            return cancelProvider.get();
        }
        catch ( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
            return null;
        }
        catch ( Exception ex )
        {
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException( ex );
        }
    }

    private boolean stoppedSuccessful( JobType type )
    {
        Future<?> cancelable = running.get( type );
        if ( cancelable == null )
        {
            log.info( "Tried to stop job of type '{}' but no job was found", type );
            return true;
        }
        boolean success = cancelable.isDone() || cancelable.cancel( true );
        log.info( String.format( "Stopped job of type: '%s' with successful result: '%b'", type, success ) );
        return success;
    }
}
