package org.truegods.binary;

public interface CronJob
{
	String getName();
	void exec(final CronStatus status) throws Throwable;
}

