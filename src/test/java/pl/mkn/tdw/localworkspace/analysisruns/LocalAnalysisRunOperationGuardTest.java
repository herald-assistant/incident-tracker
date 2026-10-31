package pl.mkn.tdw.localworkspace.analysisruns;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class LocalAnalysisRunOperationGuardTest {
    @Test void releaseAndAcquireNeverCreateTwoOwners() throws Exception {
        var guard=new LocalAnalysisRunOperationGuard();var active=new AtomicInteger();var overlaps=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(8);
        try {
            var tasks=new java.util.ArrayList<Callable<Void>>();
            for(int thread=0;thread<8;thread++)tasks.add(()->{for(int i=0;i<10000;i++) {
                var lease=guard.tryAcquire("crm");
                if(lease.isPresent())try(var held=lease.get()){
                    if(active.incrementAndGet()!=1)overlaps.incrementAndGet();
                    Thread.yield();active.decrementAndGet();
                }
            }return null;});
            for(var future:pool.invokeAll(tasks))future.get();
            assertEquals(0,overlaps.get());assertEquals(0,active.get());
            try(var lease=guard.tryAcquire("crm").orElseThrow()){assertTrue(guard.tryAcquire("crm").isEmpty());}
        }finally{pool.shutdownNow();}
    }
}
