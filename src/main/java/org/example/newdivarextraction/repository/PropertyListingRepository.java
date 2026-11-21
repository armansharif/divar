package org.example.newdivarextraction.repository;

import org.example.newdivarextraction.model.PropertyListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyListingRepository extends JpaRepository<PropertyListing, Long> {

    Optional<PropertyListing> findByToken(String token);

    boolean existsByToken(String token);

    List<PropertyListing> findByIsBrokerFalseAndIsSentFalse();

    List<PropertyListing> findByIsSentTrue();

    @Query("SELECT p.token FROM PropertyListing p WHERE p.isSent = true")
    List<String> findSentTokens();

    long countByIsBrokerTrue();

    long countByIsBrokerFalse();

    long countByIsPersonalTrue();

    // ========== Search & Filter Methods ==========

    @Query("SELECT DISTINCT p.city FROM PropertyListing p WHERE p.isBroker = false ORDER BY p.city")
    List<String> findDistinctCities();

    @Query("SELECT DISTINCT p.district FROM PropertyListing p WHERE p.isBroker = false ORDER BY p.district")
    List<String> findDistinctDistricts();

    @Query("SELECT DISTINCT p.listingType FROM PropertyListing p WHERE p.isBroker = false ORDER BY p.listingType")
    List<String> findDistinctListingTypes();

    @Query("SELECT DISTINCT p.propertyType FROM PropertyListing p WHERE p.isBroker = false ORDER BY p.propertyType")
    List<String> findDistinctPropertyTypes();

    @Query("SELECT DISTINCT p FROM PropertyListing p " +
           "LEFT JOIN FETCH p.attributes " +
           "WHERE p.isBroker = false " +
           "AND (:city IS NULL OR :city = '' OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%'))) " +
           "AND (:district IS NULL OR :district = '' OR LOWER(p.district) LIKE LOWER(CONCAT('%', :district, '%'))) " +
           "AND (:listingType IS NULL OR :listingType = '' OR p.listingType = :listingType) " +
           "ORDER BY p.scrapedAt DESC")
    Page<PropertyListing> searchListings(
            @Param("city") String city,
            @Param("district") String district,
            @Param("listingType") String listingType,
            Pageable pageable);

    @Query("SELECT DISTINCT p FROM PropertyListing p " +
           "WHERE p.isBroker = false " +
           "AND (:city IS NULL OR :city = '' OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%'))) " +
           "AND (:listingType IS NULL OR :listingType = '' OR p.listingType = :listingType) " +
           "AND (:description IS NULL OR :description = '' OR LOWER(p.description) LIKE LOWER(CONCAT('%', :description, '%'))) " +
           "ORDER BY p.scrapedAt DESC")
    Page<PropertyListing> searchListingsFull(
            @Param("city") String city,
            @Param("listingType") String listingType,
            @Param("description") String description,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM PropertyListing p WHERE p.isBroker = false")
    long countPersonal();

    @Query("SELECT COUNT(p) FROM PropertyListing p WHERE p.isBroker = false AND p.listingType = 'فروش'")
    long countForSale();

    @Query("SELECT COUNT(p) FROM PropertyListing p WHERE p.isBroker = false AND p.listingType = 'اجاره'")
    long countForRent();
}
