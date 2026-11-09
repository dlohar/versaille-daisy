# Vitis HLS (or Vivado HLS) script: C synthesis of one function.
#
# usage: vitis_hls hls_compile.tcl <project> <top function> <cpp file> [part] [clock period ns]
#   default part:  xc7z020clg400-1 (PYNQ-Z1, Zynq-7020)
#   default clock: 10 ns

set project [lindex $argv 0]
set fncName [lindex $argv 1]
set file    [lindex $argv 2]
set part    [lindex $argv 3]
set clock   [lindex $argv 4]
if {$part  eq ""} { set part  xc7z020clg400-1 }
if {$clock eq ""} { set clock 10 }

open_project -reset $project
set_top $fncName
add_files $file
open_solution -reset "solution"
set_part $part
create_clock -period $clock -name default
csynth_design
close_project
exit
