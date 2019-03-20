package org.nd4j.jita.allocator.impl;

import lombok.NonNull;
import org.nd4j.linalg.jcublas.buffer.BaseCudaDataBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CudaDeallocator implements Deallocator {

    private AllocationPoint point;
    private Map<Long, AllocationPoint> allocationsMap = new ConcurrentHashMap<>();

    public CudaDeallocator(@NonNull BaseCudaDataBuffer buffer,
                           @NonNull AllocationsMap allocationsMap) {
        this.point = buffer.getAllocationPoint();
        this.allocationsMap = allocationsMap;
    }

    @Override
    public void deallocate() {
        log.trace("Deallocating CUDA memory");
        // skipping any allocation that is coming from workspace
        if (point.isAttached()) {
            // TODO: remove allocation point as well?
            if (!allocationsMap.containsKey(point.getObjectId()))
                throw new RuntimeException();

            AtomicAllocator.getInstance().getFlowController().waitTillReleased(point);

            AtomicAllocator.getInstance().getFlowController().getEventsProvider().storeEvent(point.getLastWriteEvent());
            AtomicAllocator.getInstance().getFlowController().getEventsProvider().storeEvent(point.getLastReadEvent());

            allocationsMap.remove(point.getObjectId());

            return;
        }


        //log.info("Purging {} bytes...", AllocationUtils.getRequiredMemory(point.getShape()));
        if (point.getAllocationStatus() == AllocationStatus.HOST) {
            AtomicAllocator.getInstance().purgeZeroObject(point.getBucketId(), point.getObjectId(), point, false);
        } else if (point.getAllocationStatus() == AllocationStatus.DEVICE) {
            AtomicAllocator.getInstance().purgeDeviceObject(0L, point.getDeviceId(), point.getObjectId(), point, false);

            // and we deallocate host memory, since object is dereferenced
            AtomicAllocator.getInstance().purgeZeroObject(point.getBucketId(), point.getObjectId(), point, false);
        }
    }
}