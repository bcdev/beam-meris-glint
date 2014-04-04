 
function sol_irad_37,doy
	dtor=1.7453292e-02  ; degree to radiance
	fi=(read_ascii("subs/aatsr_ir37.dat",data_start=3)).field1
	so=(read_ascii("subs/cahalan.d")).field1
	norm=int_tabulated(fi[0,*],fi[1,*])
	soi=interpol(so[1,*],so[0,*]/1000.,fi[0,*])
	ra=int_tabulated(fi[0,*],fi[1,*]*soi)/norm
	rsun=1.-0.01673*cos(.9856*(doy-2.)*dtor)
	print,"Ra=",ra
	print,""
	return,ra/(rsun^2)*10. ; to be in consistent units
end
