package com.lekodevs.wonderbank.controller.mobile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.lekodevs.wonderbank.entity.param.EntityResponse;
import com.lekodevs.wonderbank.service.WEntityAccountService;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
	@Autowired
	WEntityAccountService entityAccountService;
	
	@GetMapping
	@ResponseBody
	public ResponseEntity<Object> dashboard() {
		return EntityResponse.generateResponse("Dashboard", HttpStatus.OK, entityAccountService.getAccountBalance());
	}

}
