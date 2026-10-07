package app.acmelabs.taskbridge.server.acquire;

import org.flowable.common.engine.impl.Page;
import org.flowable.job.service.JobServiceConfiguration;
import org.flowable.job.service.impl.ExternalWorkerJobAcquireBuilderImpl;
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity;
import org.flowable.job.service.impl.persistence.entity.data.impl.MybatisExternalWorkerJobDataManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Only offers external worker jobs that Flowable can actually lock.
 *
 * <p>{@code AcquireExternalWorkerJobsCmd} locks the whole batch in one transaction, and for an exclusive job it also
 * locks the job's process instance. That second lock fails if the instance is already locked, which rolls the whole
 * batch back and returns an empty list. Two cases cause it:
 * <ul>
 *     <li>the batch holds two exclusive jobs of one process instance: every batch fails the same way, so the jobs
 *     stay unlocked for good;</li>
 *     <li>an exclusive sibling is already in flight: every batch that includes its waiting siblings fails, so jobs of
 *     other process instances on the topic wait behind them.</li>
 * </ul>
 *
 * <p>The query ({@code ExclusiveAwareExternalWorkerJob.xml}) leaves out exclusive jobs whose process instance holds a
 * live lock, so they never take up room in the page however many pile up. This class then keeps only the first
 * exclusive job of each process instance in the page. Locking itself is unchanged, so an exclusive job still never
 * runs alongside another exclusive job of its process instance; the filter only stops Flowable from being handed
 * jobs it would refuse.
 *
 * <p>Two workers can still pick different jobs of one instance at the same moment. The loser's lock fails and
 * Flowable's acquire retry runs the query again, which by then sees the lock and skips the job.
 *
 * <p>Case instance (CMMN) jobs are passed through unfiltered.
 */
public class ExclusiveAwareExternalWorkerJobDataManager extends MybatisExternalWorkerJobDataManager {

    static final String SELECT_STATEMENT = "selectExclusiveAwareExternalWorkerJobsToExecute";

    private final int lookahead;

    /**
     * @param lookahead how many candidates to read beyond the number requested, so that dropping the second and later
     *                  exclusive jobs of a process instance still fills the batch.
     */
    public ExclusiveAwareExternalWorkerJobDataManager(JobServiceConfiguration jobServiceConfiguration, int lookahead) {
        super(jobServiceConfiguration);
        if (lookahead < 0) {
            throw new IllegalArgumentException("lookahead must not be negative");
        }
        this.lookahead = lookahead;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ExternalWorkerJobEntity> findExternalJobsToExecute(ExternalWorkerJobAcquireBuilderImpl builder, int numberOfJobs) {
        AcquireQuery query = new AcquireQuery(builder, jobServiceConfiguration.getClock().getCurrentTime());
        List<ExternalWorkerJobEntity> candidates = getDbSqlSession()
                .selectList(SELECT_STATEMENT, query, new Page(0, numberOfJobs + lookahead));

        List<ExternalWorkerJobEntity> acquirable = new ArrayList<>(Math.min(numberOfJobs, candidates.size()));
        Set<String> exclusiveInstancesTaken = new HashSet<>();
        for (ExternalWorkerJobEntity job : candidates) {
            if (acquirable.size() >= numberOfJobs) {
                break;
            }
            if (job.isExclusive() && job.getProcessInstanceId() != null
                    && !exclusiveInstancesTaken.add(job.getProcessInstanceId())) {
                continue;
            }
            acquirable.add(job);
        }
        return acquirable;
    }

    /**
     * The acquire builder's criteria plus the current engine time, which the lock condition compares against. The
     * engine clock rather than the database's, because Flowable writes {@code LOCK_TIME_} from the engine clock.
     */
    public static class AcquireQuery {

        private final ExternalWorkerJobAcquireBuilderImpl builder;
        private final Date now;

        AcquireQuery(ExternalWorkerJobAcquireBuilderImpl builder, Date now) {
            this.builder = builder;
            this.now = now;
        }

        public String getTopic() { return builder.getTopic(); }
        public String getScopeType() { return builder.getScopeType(); }
        public String getTenantId() { return builder.getTenantId(); }
        public String getAuthorizedUser() { return builder.getAuthorizedUser(); }
        public Collection<String> getAuthorizedGroups() { return builder.getAuthorizedGroups(); }
        public Date getNow() { return now; }
        public boolean isExclusive() { return true; }
    }
}
