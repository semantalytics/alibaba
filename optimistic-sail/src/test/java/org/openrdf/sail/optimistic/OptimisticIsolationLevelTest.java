package org.openrdf.sail.optimistic;

import org.openrdf.sail.NotifyingSail;
import org.openrdf.sail.SailException;
import org.openrdf.sail.memory.MemoryStore;

public class OptimisticIsolationLevelTest extends SailIsolationLevelTest {

	@Override
	protected NotifyingSail createSail()
		throws SailException
	{
		return new OptimisticSail(new MemoryStore());
	}
}
