package com.chex.user.repository;

import com.chex.user.model.VisitedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisitedPlacesRepository extends JpaRepository<VisitedPlace, Long> {
    List<VisitedPlace> findByUserid(Long userid);
    boolean existsByUseridAndPlaceid(Long userid, String placeid);

    long countByPlaceid(String placeid);
}
