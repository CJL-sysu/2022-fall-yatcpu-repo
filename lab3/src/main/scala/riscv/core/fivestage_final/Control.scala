// Copyright 2021 Howard Lau
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package riscv.core.fivestage_final

import chisel3._
import riscv.Parameters

class Control extends Module {
  val io = IO(new Bundle {
    val jump_flag = Input(Bool())                                  //id.io.if_jump_flag
    val jump_instruction_id = Input(Bool())                        //id.io.ctrl_jump_instruction           //
    val rs1_id = Input(UInt(Parameters.PhysicalRegisterAddrWidth)) //id.io.regs_reg1_read_address
    val rs2_id = Input(UInt(Parameters.PhysicalRegisterAddrWidth)) //id.io.regs_reg2_read_address
    val memory_read_enable_ex = Input(Bool())                      //id2ex.io.output_memory_read_enable
    val rd_ex = Input(UInt(Parameters.PhysicalRegisterAddrWidth))  //id2ex.io.output_regs_write_address
    val memory_read_enable_mem = Input(Bool())                     //ex2mem.io.output_memory_read_enable   //
    val rd_mem = Input(UInt(Parameters.PhysicalRegisterAddrWidth)) //ex2mem.io.output_regs_write_address   //

    val if_flush = Output(Bool())
    val id_flush = Output(Bool())
    val pc_stall = Output(Bool())
    val if_stall = Output(Bool())
  })

  // Lab3(Final)
  io.if_flush := false.B
  io.id_flush := false.B
  io.pc_stall := false.B
  io.if_stall := false.B
  when((io.memory_read_enable_ex && io.rd_ex =/= 0.U && (io.rd_ex === io.rs1_id || io.rd_ex === io.rs2_id))||
    (io.memory_read_enable_mem && io.rd_mem =/= 0.U && (io.rd_mem === io.rs1_id || io.rd_mem === io.rs2_id))) {
    io.id_flush := true.B
    io.pc_stall := true.B
    io.if_stall := true.B
  }.elsewhen(io.jump_instruction_id){
    io.if_flush := true.B
  }
  // Lab3(Final) End
}
