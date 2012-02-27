package org.truegods.binary;

public abstract class CronStatus implements Comparable<CronStatus>
{
	public final CronJob job;
	public long runs;
	public long nextRun;

	protected CronStatus(final CronJob job)
	{
		this.job = job;
		this.nextRun = Long.MIN_VALUE;
	}

	/**
	 * Sets nextRun to the next time this job should run
	 */
	public abstract void reschedule();

	@SuppressWarnings({"CompareToUsesNonFinalVariable"}) @Override public int compareTo(final CronStatus o)
	{
		return nextRun < o.nextRun ? -1 : nextRun == o.nextRun ? job.getName().compareTo(o.job.getName()) : 1;
	}

	public void cancel()
	{
		Cron.cancel(this);
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final CronStatus status = (CronStatus) o;
		return nextRun == status.nextRun &&
				runs == status.runs &&
				!(job != null ? !job.equals(status.job) : status.job != null);
	}

	@Override
	public int hashCode()
	{
		int result = job != null ? job.hashCode() : 0;
		result = 31 * result + (int) (runs ^ runs >>> 32);
		result = 31 * result + (int) (nextRun ^ nextRun >>> 32);
		return result;
	}
}

