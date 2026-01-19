package com.example.bloodsystem.repository;

import com.example.bloodsystem.entity.Donor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface DonorRepository extends JpaRepository<Donor, String>, JpaSpecificationExecutor<Donor> {
    @Query("SELECT d FROM Donor d WHERE d.donorId LIKE %?1% OR d.name LIKE %?1%")
    Page<Donor> search(String keyword, Pageable pageable);
}