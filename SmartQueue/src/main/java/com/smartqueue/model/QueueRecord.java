package com.smartqueue.model;

import java.time.LocalDate;
import java.time.LocalDateTime;


public class QueueRecord {

    private int           queueId;
    private int           departmentId;
    private int           priorityId;
    private String        status;
    private LocalDate     queueDate;
    private LocalDateTime createdAt;    // when the patient joined the queue
    private LocalDateTime calledAt;     // when the patient left the waiting list
    private LocalDateTime startedAt;    // when service started
    private LocalDateTime completedAt;  // when service finished

    public QueueRecord() {}

    public int           getQueueId()      { return queueId; }
    public int           getDepartmentId() { return departmentId; }
    public int           getPriorityId()   { return priorityId; }
    public String        getStatus()       { return status; }
    public LocalDate     getQueueDate()    { return queueDate; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getCalledAt()     { return calledAt; }
    public LocalDateTime getStartedAt()    { return startedAt; }
    public LocalDateTime getCompletedAt()  { return completedAt; }

    public void setQueueId(int queueId)                  { this.queueId = queueId; }
    public void setDepartmentId(int departmentId)        { this.departmentId = departmentId; }
    public void setPriorityId(int priorityId)            { this.priorityId = priorityId; }
    public void setStatus(String status)                 { this.status = status; }
    public void setQueueDate(LocalDate queueDate)        { this.queueDate = queueDate; }
    public void setCreatedAt(LocalDateTime createdAt)    { this.createdAt = createdAt; }
    public void setCalledAt(LocalDateTime calledAt)      { this.calledAt = calledAt; }
    public void setStartedAt(LocalDateTime startedAt)    { this.startedAt = startedAt; }
    public void setCompletedAt(LocalDateTime completedAt){ this.completedAt = completedAt; }
}