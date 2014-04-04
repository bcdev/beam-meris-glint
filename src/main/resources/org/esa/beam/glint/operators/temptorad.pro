function temptorad,temp
	common temptorad_intern,base
	if (size(base,/type) eq 0) then base=(read_ascii("subs/temp_to_rad_36.d")).field1
	return,interpol(base[1,*],base[0,*],temp); W / m2 / um / sr	
end

