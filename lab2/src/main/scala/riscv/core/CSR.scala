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
import chisel3.util._
import riscv.Parameters


object CSRRegister {
  // Refer to Spec. Vol.II Page 8-10
  val MSTATUS = 0x300.U(Parameters.CSRRegisterAddrWidth)
  val MIE = 0x304.U(Parameters.CSRRegisterAddrWidth)
  val MTVEC = 0x305.U(Parameters.CSRRegisterAddrWidth)
  val MSCRATCH = 0x340.U(Parameters.CSRRegisterAddrWidth)
  val MEPC = 0x341.U(Parameters.CSRRegisterAddrWidth)
  val MCAUSE = 0x342.U(Parameters.CSRRegisterAddrWidth)
  val CycleL = 0xc00.U(Parameters.CSRRegisterAddrWidth)
  val CycleH = 0xc80.U(Parameters.CSRRegisterAddrWidth)
}

class CSR extends Module {
  val io = IO(new Bundle {
    val reg_read_address_id = Input(UInt(Parameters.CSRRegisterAddrWidth))    //CSRRA
    val reg_write_enable_id= Input(Bool())                                    //CSRWE
    val reg_write_address_id = Input(UInt(Parameters.CSRRegisterAddrWidth))   //CSRWA
    val reg_write_data_ex= Input(UInt(Parameters.DataWidth))                  //CSRWD
    val debug_reg_read_address = Input(UInt(Parameters.CSRRegisterAddrWidth))

    val debug_reg_read_data = Output(UInt(Parameters.DataWidth))
    val reg_read_data = Output(UInt(Parameters.DataWidth))                    //CSRRD

    val clint_access_bundle = Flipped(new CSRDirectAccessBundle)              //和CLINT连接的八根线
  })

  val mstatus = RegInit(UInt(Parameters.DataWidth), 0.U)  //在响应中断时，需要将 mstatus 寄存器中的 MPIE 标志位设置为 0，禁用中断。
  val mie = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mtvec = RegInit(UInt(Parameters.DataWidth), 0.U)    //从 mtvec 获取中断处理程序的地址，跳转到该地址执行进一步的中断处理
  val mscratch = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mepc = RegInit(UInt(Parameters.DataWidth), 0.U)     //保存的是中断或者异常处理完成后，CPU返回并开始执行的地址。所以对于异常和中断，mepc 的保存内容需要注意
  val mcause = RegInit(UInt(Parameters.DataWidth), 0.U)   //保存的是导致中断或者异常的原因
  val cycles = RegInit(UInt(64.W), 0.U)
  val regLUT =
    IndexedSeq(
      CSRRegister.MSTATUS -> mstatus,
      CSRRegister.MIE -> mie,
      CSRRegister.MTVEC -> mtvec,
      CSRRegister.MSCRATCH -> mscratch,
      CSRRegister.MEPC -> mepc,
      CSRRegister.MCAUSE -> mcause,
      CSRRegister.CycleL -> cycles(31, 0),
      CSRRegister.CycleH -> cycles(63, 32),
    )
  cycles := cycles + 1.U

  // If the pipeline and the CLINT are going to read and write the CSR at the same time, let the pipeline write first.
  // This is implemented in a single cycle by passing reg_write_data_ex to clint and writing the data from the CLINT to the CSR.
  io.reg_read_data := MuxLookup(io.reg_read_address_id, 0.U, regLUT)
  io.debug_reg_read_data := MuxLookup(io.debug_reg_read_address, 0.U,regLUT)

  //以下对应原理图中CSR和CLINT之间的左四根线
  //lab2(CLINTCSR)
  //what data should be passed from csr to clint (Note: what should clint see is the next state of the CPU)

  io.clint_access_bundle.mstatus := Mux(io.reg_write_enable_id && io.reg_write_address_id===CSRRegister.MSTATUS,io.reg_write_data_ex,mstatus)
  io.clint_access_bundle.mtvec := Mux(io.reg_write_enable_id && io.reg_write_address_id===CSRRegister.MTVEC,io.reg_write_data_ex,mtvec)
  io.clint_access_bundle.mcause := Mux(io.reg_write_enable_id && io.reg_write_address_id===CSRRegister.MCAUSE,io.reg_write_data_ex,mcause)
  io.clint_access_bundle.mepc := Mux(io.reg_write_enable_id && io.reg_write_address_id===CSRRegister.MEPC,io.reg_write_data_ex,mepc)
  //lab2 end

  //以下对应原理图中CSR和CLINT之间的右四根线
  when(io.clint_access_bundle.direct_write_enable) {
    mstatus := io.clint_access_bundle.mstatus_write_data
    mepc := io.clint_access_bundle.mepc_write_data
    mcause := io.clint_access_bundle.mcause_write_data
  }.elsewhen(io.reg_write_enable_id) {
    when(io.reg_write_address_id === CSRRegister.MSTATUS) {
      mstatus := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MEPC) {
      mepc := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MCAUSE) {
      mcause := io.reg_write_data_ex
    }
  }

  when(io.reg_write_enable_id) {
    when(io.reg_write_address_id === CSRRegister.MIE) {
      mie := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MTVEC){
      mtvec := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MSCRATCH) {
      mscratch := io.reg_write_data_ex
    }
  }


}
