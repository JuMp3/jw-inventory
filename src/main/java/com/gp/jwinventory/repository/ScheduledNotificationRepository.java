package com.gp.jwinventory.repository;

import com.gp.jwinventory.entity.ScheduledNotification;
import com.gp.jwinventory.enums.NotificationScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, Long> {

    /** SuperAdmin view: every notification. */
    @Query("select n from ScheduledNotification n left join fetch n.congregation order by n.name")
    List<ScheduledNotification> findAllOrderByName();

    /** Admin/user view: ADMIN-scope notifications of the congregation plus the personal ones the user owns. */
    @Query("""
            select n from ScheduledNotification n left join fetch n.congregation c left join n.owner o
            where (n.scope = com.gp.jwinventory.enums.NotificationScope.ADMIN and c.id = :congregationId)
               or (n.scope = com.gp.jwinventory.enums.NotificationScope.USER and o.id = :ownerId)
            order by n.name""")
    List<ScheduledNotification> findVisibleTo(@Param("ownerId") Long ownerId, @Param("congregationId") Long congregationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ScheduledNotification n where n.enabled = true and n.nextRunAt <= :now order by n.nextRunAt")
    List<ScheduledNotification> findDueForUpdate(@Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from ScheduledNotification n where n.scope = :scope and n.owner.id = :ownerId")
    void deleteByScopeAndOwnerId(@Param("scope") NotificationScope scope, @Param("ownerId") Long ownerId);
}
