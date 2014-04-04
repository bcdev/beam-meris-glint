function date2doy,dtstr
	; this is rather ugly, since idl is not good with dates
	; separate the string
	dum=(str_sep(dtstr," "))[0]
	dum=str_sep(dum,"-")
	; convert month name to month number
	date=bin_date("asd "+dum[1]+" "+dum[0]+" 11:11:11 "+dum[2])
	;convert to day of year
	doy = (julday(date[2],date[1],date[0],12,0,0)-julday(1,1,date[0],12,0,0)) +1 
	return,doy
end
