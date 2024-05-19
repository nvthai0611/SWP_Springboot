package com.lekodevs.wonderbank.controller.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.lekodevs.wonderbank.entity.WEntity;
import com.lekodevs.wonderbank.entity.param.EntityResponse;
import com.lekodevs.wonderbank.security.SecurityPrincipal;
import com.lekodevs.wonderbank.service.WLookupService;

@RestController
@RequestMapping("lookup")
public class LookupController {

	@Autowired
	private WLookupService lookupService;
	@GetMapping("account-type")
	@ResponseBody
	public ResponseEntity<Object> getAccountType() {
		return EntityResponse.generateResponse("Retrieve Lookup Success", HttpStatus.OK, lookupService.retrieveAccountTypeLookup());
	}
}
