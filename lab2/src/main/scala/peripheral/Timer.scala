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

package peripheral

import chisel3._
import chisel3.util._
import riscv.Parameters

class Timer extends Module {
  val io = IO(new Bundle {
    val bundle = new RAMBundle
    val signal_interrupt = Output(Bool())

    val debug_limit = Output(UInt(Parameters.DataWidth))
    val debug_enabled = Output(Bool())
  })

  val count = RegInit(0.U(32.W))
  val limit = RegInit(100000000.U(32.W))
  io.debug_limit := limit
  val enabled = RegInit(true.B)
  io.debug_enabled := enabled

  //lab2(CLINTCSR)
  //finish the read-write for count,limit,enabled. And produce appropriate signal_interrupt
  //读

  io.bundle.read_data := limit

  when(io.bundle.address === 8.U){
    when(enabled){
      io.bundle.read_data:=1.U
    }
      .otherwise{
        io.bundle.read_data:=0.U
      }
  }
  //写
  when(io.bundle.write_enable){
    when(io.bundle.address === 4.U){
      limit := io.bundle.write_data
    }
      .elsewhen(io.bundle.address === 8.U){
        enabled := io.bundle.write_data =/= 0.U
      }
  }
  //计时
  io.signal_interrupt := false.B
  when(count === limit)
  {
    count := 0.U
    when(enabled){
      io.signal_interrupt := true.B
    }
  }
    .otherwise{
      count := count + 1.U
      io.signal_interrupt := false.B
    }
}
