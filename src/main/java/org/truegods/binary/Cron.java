package org.truegods.binary;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Cron
{
	private static final Log log = Log.getInstance(Cron.class);

	private static final ExecutorService executor;
	private static final Collection<CronStatus> jobs;
	private static boolean shuttingDown = false;

	private static final DateTimeFormatter shortDateTime = DateTimeFormat.shortDateTime();

	static
	{
		log.info("+Cron [heartbeat:1s, max simultaneous jobs:5]");
		executor = Executors.newFixedThreadPool(5);
		jobs = Collections.synchronizedSet(new HashSet<CronStatus>());
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override public void run()
			{
				shuttingDown = true;
			}
		});
		new Thread()
		{
			@Override public void run()
			{
				while (!shuttingDown)
				{
					try
					{
						final long now = System.currentTimeMillis();
						boolean foundJob = false;
						for (final CronStatus status : new ArrayList<CronStatus>(jobs))
						{
							if (status.nextRun <= now)
							{
								if (!foundJob)
								{
									foundJob = true;
									if (log.isTraceEnabled())
										log.trace("cron: heartbeat %s", shortDateTime.print(now));
								}

								log.trace("cron: %s is ready", status.job.getName());
								jobs.remove(status);
								executor.submit(new Runnable()
								{
									@Override public void run()
									{
										try
										{
											log.debug("cron: %s is running", status.job.getName());
											status.job.exec(status);
										}
										catch (Throwable t)
										{
											log.error("cron: %s had a problem", status.job.getName(), t);
										}
										try
										{
											status.runs++;
											if (status.nextRun <= now)
												status.reschedule();
											if (status.nextRun != Long.MAX_VALUE)
											{
												if (log.isDebugEnabled())
													log.debug("cron: %s is next scheduled for %s", status.job.getName(),
													          shortDateTime.print(status.nextRun));
												jobs.add(status);
											}
											else
												log.debug("cron: %s cancelled during run", status.job.getName());
										}
										catch (Throwable t)
										{
											log.error("cron: cron system error while rescheduling %s", status.job.getName(), t);
										}
									}
								});
							}
						}
						try
						{
							Thread.sleep(1000);
						}
						catch (InterruptedException e)
						{
							log.error(e.getMessage());
						}
					}
					catch (Throwable t)
					{
						log.error("cron: error in cron system", t);
					}
				}
				log.info("cron: shutting down");
				executor.shutdown();
			}
		}.start();
	}

	/**
	 * Runs the job at the given interval forever
	 * @param interval how long between runs
	 * @param unit     of measurement
	 * @param job      the task
	 * @return status
	 */
	public static CronStatus interval(final int interval, final TimeUnit unit, final CronJob job)
	{
		log.info("Scheduling %s every %d %s", job.getName(), interval, unit.name());
		final CronStatus status = new IntervalCronStatus(job, unit.toMillis(interval));
		jobs.add(status);
		return status;
	}

	/**
	 * Runs the job at the given interval the given number of times
	 * @param interval how long between runs
	 * @param unit     of measurement
	 * @param times    number of runs
	 * @param job      the task
	 * @return status
	 */
	public static CronStatus interval(final int interval, final TimeUnit unit, final long times, final CronJob job)
	{
		log.info("Scheduling %s every %d %s, limit; %d", job.getName(), interval, unit.name(), times);
		final CronStatus status = new IntervalCronStatus(job, unit.toMillis(interval), times);
		jobs.add(status);
		return status;
	}


	@SuppressWarnings({"InfiniteLoopStatement", "ConstantConditions"}) public static void waitForever()
	{
		while (true)
		{
			final ReentrantLock lock = new ReentrantLock();
			final Condition forever = lock.newCondition();
			lock.lock();
			try
			{
				forever.await();
			}
			catch (Throwable t)
			{
				log.debug("interrupted", t);
			}
			finally
			{
				lock.unlock();
			}
		}

	}

	public static void cancelAll()
	{
		log.trace("all jobs removed");
		jobs.clear();
	}

	public static void cancel(final CronStatus status)
	{
		if (jobs.remove(status))
			log.trace("cron: %s is cancelled", status.job.getName());
		else
			status.nextRun = Long.MAX_VALUE;

	}

	public static void now(final CronJob job) throws Throwable
	{
		log.info("Scheduling immediate run of %s", job.getName());
		final CronStatus status = new IntervalCronStatus(job, 0L, 1L);
		job.exec(status);
	}
}
