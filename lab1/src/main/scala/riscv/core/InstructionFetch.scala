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

package riscv.core

import chisel3._
import riscv.Parameters

object ProgramCounter {
  val EntryAddress = Parameters.EntryAddress
}

class InstructionFetch extends Module {
  val io = IO(new Bundle {
    val jump_flag_id = Input(Bool())
    val jump_address_id = Input(UInt(Parameters.AddrWidth))
    val instruction_read_data = Input(UInt(Parameters.DataWidth))//-根据PC读指令的结果
    val instruction_valid = Input(Bool())//-指令有效

    val instruction_address = Output(UInt(Parameters.AddrWidth))//-下一条指令的地址?
    val instruction = Output(UInt(Parameters.InstructionWidth))//-输出指令
  })
  val pc = RegInit(ProgramCounter.EntryAddress)

  when(io.instruction_valid) {
    io.instruction := io.instruction_read_data
    // lab1(InstructionFetch)
	pc := pc + 4.U  //指令的地址加4
	//处理jump
	when(io.jump_flag_id) {
		pc := pc + Int(io.jump_address_id)
	}

    // la1(InstructionFetch) end


  }.otherwise{
    pc := pc
    io.instruction := 0x00000013.U
  }
  io.instruction_address := pc
}
