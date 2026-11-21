package org.example.newdivarextraction.repository;

import org.example.newdivarextraction.model.FilterKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilterKeywordRepository extends JpaRepository<FilterKeyword, Long> {

    List<FilterKeyword> findByEnabledTrue();

    FilterKeyword findByKeyword(String keyword);
}
