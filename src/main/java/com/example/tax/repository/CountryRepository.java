package com.example.tax.repository;

import com.core.lib.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country,Long> {

    Optional<Country> findByCountryCode(String code);
}
