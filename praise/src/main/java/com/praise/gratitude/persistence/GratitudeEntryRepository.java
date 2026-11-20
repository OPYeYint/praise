package com.praise.gratitude.persistence;

import com.praise.gratitude.domain.GratitudeEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GratitudeEntryRepository extends JpaRepository<GratitudeEntry, Long> {

    @Query("""
            select entry from GratitudeEntry entry
            where entry.user.id = :userId
            order by entry.entryDate desc, entry.createdAt desc
            """)
    List<GratitudeEntry> findAllByUserIdOrderByEntryDateDescCreatedAtDesc(@Param("userId") Long userId);

    @Query("""
            select entry from GratitudeEntry entry
            where entry.id = :id and entry.user.id = :userId
            """)
    Optional<GratitudeEntry> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select count(entry) from GratitudeEntry entry where entry.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("select entry from GratitudeEntry entry where entry.user.id = :userId")
    Page<GratitudeEntry> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
