@subs/cm_ws_to_gauss2d_nn.pro
@subs/ref_surf_conversion.pro

;pro test_conversion
;This procedure calls "ref_surf_conversion" for several cases.
;
;ref_surf_conversion is made to convert reflect. from AATSR to MERIS geometry 
;and refractive index
;
;Use it as a reference for the BEAM implementation

for cas=1,7 do begin 

	case cas of
	1: begin   
		sun=20.
		vie=10.
		aziin=30.
		aziou=10.
		nin=1.36
		nou=1.33
		refin=0.03
		comment="uniq solution, high ws"
	end
	2: begin	 
		sun=20.
		vie=20.
		aziin=30.
		aziou=10.
		nin=1.36
		nou=1.33
		refin=0.2
		comment= "nothing found, since reflectivity is too high for that geometry"
	end
	3: begin 
		sun=20.
		vie=20.
		aziin=30.
		aziou=10.
		nin=1.36
		nou=1.33
		refin=0.02
		comment="nothing found, since reflectivity is too low for that geometry"
	end
	4: begin 
		sun=30.
		vie=40.
		aziin=30.
		aziou=10.
		nin=1.36
		nou=1.33
		refin=0.02
		comment="uniqe solution, low ws"
	end
	5: begin	 
		sun=30.
		vie=20.
		aziin=30.
		aziou=10.
		nin=1.36
		nou=1.33
		refin=0.03
		comment="two solutions (two windspeeds)"
	end
	6: begin
		sun=30.
		vie=20.
		aziin=120.
		aziou=100.
		nin=1.36
		nou=1.33
		refin=0.003
		comment="uniq solution outside the glint "
		end
	7: begin		sun=30.
		vie=20.
		aziin=20.
		aziou=20.
		nin=1.36
		nou=1.33
		refin=0.05
		comment=" non-unique = dual solution (two windspeeds)  "  $
			+ "BUT same azimut (as applicable for AATSR3.7 --> AATSR0.8 or MODIS--> MODIS) " $ 
			+ "therefor the two solutions should be the same, even if the " $
			+ "'effective windspeeds' differs"

		end
	endcase
		
	ref_meris=ref_surf_conversion(refin,sun,vie,aziin,aziou,nin,nou		$
			,nothing_found=nothing_found,ambiguity=ambiguity	$
			,effective_ws=effective_ws)
	
	print,comment
	print,"refin=",refin
	print,"ref_meris=",ref_meris
	print,"effective ws=",effective_ws
	print,"nothing_found_flag=",nothing_found
	print,"ambiguity_flag=",ambiguity
	if cas lt 7 then begin
		print, "press 'anykey' to continue"
		dum=get_kbrd(1)
	endif
endfor

end
