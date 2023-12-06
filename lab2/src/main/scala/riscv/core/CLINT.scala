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
import chisel3.util.MuxLookup
import riscv.Parameters

object InterruptStatus {
  val None = 0x0.U(8.W)
  val Timer0 = 0x1.U(8.W)
  val Ret = 0xFF.U(8.W)
}

object InterruptEntry {
  val Timer0 = 0x4.U(8.W)
}

object InterruptState {
  val Idle = 0x0.U
  val SyncAssert = 0x1.U
  val AsyncAssert = 0x2.U
  val MRET = 0x3.U
}

object CSRState {
  val Idle = 0x0.U    //空闲
  val Traping = 0x1.U
  val Mret = 0x2.U
}

class CSRDirectAccessBundle extends Bundle {
  val mstatus = Input(UInt(Parameters.DataWidth))
  val mepc = Input(UInt(Parameters.DataWidth))
  val mcause = Input(UInt(Parameters.DataWidth))
  val mtvec = Input(UInt(Parameters.DataWidth))

  val mstatus_write_data= Output(UInt(Parameters.DataWidth))
  val mepc_write_data= Output(UInt(Parameters.DataWidth))
  val mcause_write_data= Output(UInt(Parameters.DataWidth))

  val direct_write_enable = Output(Bool())
}

// Core Local Interrupt Controller
class CLINT extends Module {
  val io = IO(new Bundle {
    // Interrupt signals from peripherals
    val interrupt_flag = Input(UInt(Parameters.InterruptFlagWidth))

    val instruction = Input(UInt(Parameters.InstructionWidth))
    val instruction_address = Input(UInt(Parameters.AddrWidth))   //IFInsAddr

    val jump_flag = Input(Bool())
    val jump_address = Input(UInt(Parameters.AddrWidth))

    val interrupt_handler_address = Output(UInt(Parameters.AddrWidth))   //中断处理程序的地址
    val interrupt_assert = Output(Bool())                                //是否中断的标志

    val csr_bundle = new CSRDirectAccessBundle                           //图中CSR和CLINT之间的八根线
  })
  val interrupt_enable = io.csr_bundle.mstatus(3)  //mstatus第三位是中断的使能
  val instruction_address = Mux(
    io.jump_flag,
    io.jump_address,
    io.instruction_address + 4.U,
  )
  //lab2(CLINTCSR)

  //val interrupt_enable =

  when(io.interrupt_flag =/= InterruptStatus.None && interrupt_enable) {   //  =/=表示不等
    io.csr_bundle.mstatus_write_data := io.csr_bundle.mstatus(31,13)## 3.U(2.W) ## io.csr_bundle.mstatus(10,4) ## 0.U(1.W) ## io.csr_bundle.mstatus(2,0)//特权级别设置为M-mode//修改MIE位，关中断
    io.csr_bundle.mepc_write_data := instruction_address    //保存下一条指令的地址
    io.csr_bundle.mcause_write_data := Mux(io.interrupt_flag(0),0x80000007L.U,0x8000000BL.U)//io.interrupt_flag(0)为1表示由timer产生的外部中断，对应mcause的值0x80000007L.U,否则是uart造成的软件中断，见特权级手册39页
    io.csr_bundle.direct_write_enable := true.B
    io.interrupt_assert := true.B
    io.interrupt_handler_address := io.csr_bundle.mtvec
  }.elsewhen(io.instruction === InstructionsRet.mret) {
    io.csr_bundle.mstatus_write_data := io.csr_bundle.mstatus(31,13)## 3.U(2.W) ## io.csr_bundle.mstatus(10,4) ## 1.U(1.W) ## io.csr_bundle.mstatus(2,0)//特权级别设置为M-mode//修改MIE位，开中断
    io.csr_bundle.mepc_write_data := io.csr_bundle.mepc                              //保持原样
    io.csr_bundle.mcause_write_data := io.csr_bundle.mcause
    io.csr_bundle.direct_write_enable := true.B
    io.interrupt_assert := true.B
    io.interrupt_handler_address := io.csr_bundle.mepc
  }.otherwise {
    io.csr_bundle.mstatus_write_data := io.csr_bundle.mstatus                         //保持原样
    io.csr_bundle.mepc_write_data := io.csr_bundle.mepc                               //保持原样
    io.csr_bundle.mcause_write_data := io.csr_bundle.mcause
    io.csr_bundle.direct_write_enable := false.B
    io.interrupt_assert := false.B
    io.interrupt_handler_address :=io.csr_bundle.mtvec                                 //don't care
  }

}
