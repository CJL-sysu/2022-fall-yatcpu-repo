// Copyright 2022 Canbin Huang
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

package riscv.core.fivestage_forward

import chisel3._
import riscv.Parameters

object ForwardingType {
  val NoForward = 0.U(2.W)
  val ForwardFromMEM = 1.U(2.W)
  val ForwardFromWB = 2.U(2.W)
}

class Forwarding extends Module {
  val io = IO(new Bundle() {
    val rs1_ex = Input(UInt(Parameters.PhysicalRegisterAddrWidth))  //id2ex.io.output_regs_reg1_read_address
    val rs2_ex = Input(UInt(Parameters.PhysicalRegisterAddrWidth))  //id2ex.io.output_regs_reg2_read_address
    val rd_mem = Input(UInt(Parameters.PhysicalRegisterAddrWidth))  //ex2mem.io.output_regs_write_address
    val reg_write_enable_mem = Input(Bool())                        //ex2mem.io.output_regs_write_enable
    val rd_wb = Input(UInt(Parameters.PhysicalRegisterAddrWidth))   //mem2wb.io.output_regs_write_address
    val reg_write_enable_wb = Input(Bool())                         //mem2wb.io.output_regs_write_enable

    // Forwarding Type
    val reg1_forward_ex = Output(UInt(2.W))                         //ex.io.reg1_forward
    val reg2_forward_ex = Output(UInt(2.W))                         //ex.io.reg2_forward
  })

  // Lab3(Forward)
  //用一个旁路单元来检测数据冒险并发出旁路控制信号，模块接口已经定义在这里
  when(io.reg_write_enable_mem && io.rs1_ex === io.rd_mem && io.rd_mem =/= 0.U){
    io.reg1_forward_ex := ForwardingType.ForwardFromMEM
  }.elsewhen(io.reg_write_enable_wb && io.rs1_ex === io.rd_wb && io.rd_wb =/= 0.U){
    io.reg1_forward_ex := ForwardingType.ForwardFromWB
  }.otherwise{
    io.reg1_forward_ex := ForwardingType.NoForward
  }
  when(io.reg_write_enable_mem && io.rs2_ex === io.rd_mem && io.rd_mem =/= 0.U){
    io.reg2_forward_ex := ForwardingType.ForwardFromMEM
  }.elsewhen(io.reg_write_enable_wb && io.rs2_ex === io.rd_wb && io.rd_wb =/= 0.U){
    io.reg2_forward_ex := ForwardingType.ForwardFromWB
  }.otherwise{
    io.reg2_forward_ex := ForwardingType.NoForward
  }


//  io.reg1_forward_ex := ForwardingType.NoForward
//  io.reg2_forward_ex := ForwardingType.NoForward
//  when(io.reg_write_enable_mem && (io.rs1_ex === io.rd_mem || io.rs2_ex === io.rd_mem) && io.rd_mem =/= 0.U){
//    when(io.rs1_ex === io.rd_mem){
//      io.reg1_forward_ex := ForwardingType.ForwardFromMEM
//    }.elsewhen(io.rs2_ex === io.rd_mem){
//      io.reg2_forward_ex := ForwardingType.ForwardFromMEM
//    }
//  }.elsewhen(io.reg_write_enable_wb && (io.rs1_ex === io.rd_wb || io.rs2_ex === io.rd_wb) && io.rd_wb =/= 0.U){
//    when(io.rs1_ex === io.rd_wb){
//      io.reg1_forward_ex := ForwardingType.ForwardFromWB
//    }.elsewhen(io.rs2_ex === io.rd_wb){
//      io.reg2_forward_ex := ForwardingType.ForwardFromWB
//    }
//  }
  // Lab3(Forward) End
}
