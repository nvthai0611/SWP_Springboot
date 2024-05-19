package com.lekodevs.wonderbank.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lekodevs.wonderbank.entity.WVerificationCode;

@Repository
public interface IVerificationCodeRepository extends JpaRepository<WVerificationCode, Long>{
	WVerificationCode findByEntityNoAndCode(String entityNo, String token);
}
