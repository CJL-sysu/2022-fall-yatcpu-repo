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

object InstructionTypes {  // 枚举了opcode,这里是一些指令大类
  val L = "b0000011".U   //load类型（属于i型）
  val I = "b0010011".U   //i型
  val S = "b0100011".U   //s型
  val RM = "b0110011".U  //R型
  val B = "b1100011".U   //B型
}

object Instructions {     // 同样枚举了opcode
  val lui = "b0110111".U  //U型
  val nop = "b0000001".U  //啥也不干
  val jal = "b1101111".U  //J型
  val jalr = "b1100111".U //I型
  val auipc = "b0010111".U  //U型
  val csr = "b1110011".U
  val fence = "b0001111".U
}

object InstructionsTypeL {//load指令，属于I型，以下枚举funct3的值
  val lb = "b000".U
  val lh = "b001".U
  val lw = "b010".U
  val lbu = "b100".U
  val lhu = "b101".U
}

object InstructionsTypeI {//I型指令
  val addi = 0.U
  val slli = 1.U
  val slti = 2.U
  val sltiu = 3.U
  val xori = 4.U
  val sri = 5.U
  val ori = 6.U
  val andi = 7.U
}

object InstructionsTypeS {//S型
  val sb = "b000".U
  val sh = "b001".U
  val sw = "b010".U
}

object InstructionsTypeR {//R型
  val add_sub = 0.U
  val sll = 1.U
  val slt = 2.U
  val sltu = 3.U
  val xor = 4.U
  val sr = 5.U
  val or = 6.U
  val and = 7.U
}

object InstructionsTypeM {//R型，和乘法有关
  val mul = 0.U
  val mulh = 1.U
  val mulhsu = 2.U
  val mulhum = 3.U
  val div = 4.U
  val divu = 5.U
  val rem = 6.U
  val remu = 7.U
}

object InstructionsTypeB {//B型
  val beq = "b000".U
  val bne = "b001".U
  val blt = "b100".U
  val bge = "b101".U
  val bltu = "b110".U
  val bgeu = "b111".U
}

object InstructionsTypeCSR {
  val csrrw = "b001".U
  val csrrs = "b010".U
  val csrrc = "b011".U
  val csrrwi = "b101".U
  val csrrsi = "b110".U
  val csrrci = "b111".U
}

object InstructionsNop {
  val nop = 0x00000013L.U(Parameters.DataWidth)
}

object InstructionsRet {
  val mret = 0x30200073L.U(Parameters.DataWidth)
  val ret = 0x00008067L.U(Parameters.DataWidth)
}

object InstructionsEnv {
  val ecall = 0x00000073L.U(Parameters.DataWidth)
  val ebreak = 0x00100073L.U(Parameters.DataWidth)
}

object ALUOp1Source {
  val Register = 0.U(1.W)
  val InstructionAddress = 1.U(1.W)
}

object ALUOp2Source {
  val Register = 0.U(1.W)
  val Immediate = 1.U(1.W)
}

object RegWriteSource {
  val ALUResult = 0.U(2.W)
  val Memory = 1.U(2.W)
  //val CSR = 2.U(2.W)
  val NextInstructionAddress = 3.U(2.W)
}

class InstructionDecode extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(Parameters.InstructionWidth))
    //这两个指定读寄存器组的两个寄存器地址
    val regs_reg1_read_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))
    val regs_reg2_read_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))
    val ex_immediate = Output(UInt(Parameters.DataWidth))//立即数
    //这两个操作ALU入口处的两个MUX
    val ex_aluop1_source = Output(UInt(1.W))
    val ex_aluop2_source = Output(UInt(1.W))
    val memory_read_enable = Output(Bool())//读内存的使能
    val memory_write_enable = Output(Bool())//写内存的使能
    val wb_reg_write_source = Output(UInt(2.W))//控制ALU出口的MUX(多路复用器)，选一个写回寄存器组
    val reg_write_enable = Output(Bool())//写寄存器的使能
    val reg_write_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))//写寄存器的地址
  })
  //拆分指令
  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)
  val rd = io.instruction(11, 7)
  val rs1 = io.instruction(19, 15)
  val rs2 = io.instruction(24, 20)

  io.regs_reg1_read_address := Mux(opcode === Instructions.lui, 0.U(Parameters.PhysicalRegisterAddrWidth), rs1)//这里需要对lui指令特殊处理,保证lui在这里取rs1=0
  io.regs_reg2_read_address := rs2
  val immediate = MuxLookup(
    opcode,
    Cat(Fill(20, io.instruction(31)), io.instruction(31, 20)),
    IndexedSeq(   //Cat的作用是符号扩展
      InstructionTypes.I -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      InstructionTypes.L -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      Instructions.jalr -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      InstructionTypes.S -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 25), io.instruction(11, 7)),
      InstructionTypes.B -> Cat(Fill(20, io.instruction(31)), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), 0.U(1.W)),
      Instructions.lui -> Cat(io.instruction(31, 12), 0.U(12.W)),
      Instructions.auipc -> Cat(io.instruction(31, 12), 0.U(12.W)),
      Instructions.jal -> Cat(Fill(12, io.instruction(31)), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), 0.U(1.W))
    )
  )//用于获取立即数
  io.ex_immediate := immediate
  io.ex_aluop1_source := Mux(
    opcode === Instructions.auipc || opcode === InstructionTypes.B || opcode === Instructions.jal,
    ALUOp1Source.InstructionAddress,  //1,将指令地址传入ALU进行计算
    ALUOp1Source.Register  //0
  )//这一部分网页上的图片画错了

  // lab1(InstructionDecode)
  //补充为 io.ex_aluop2_source、io.memory_read_enable、io.memory_write_enable、io.wb_reg_write_source 四个控制信号赋值的代码
  //io.ex_aluop2_source
  io.ex_aluop2_source := Mux(
    opcode === InstructionTypes.L || opcode === InstructionTypes.I || opcode === Instructions.jalr
      || opcode === Instructions.jal || opcode === InstructionTypes.S || opcode === InstructionTypes.B ||
      opcode === Instructions.lui || opcode === Instructions.auipc,
    ALUOp2Source.Immediate,
    ALUOp2Source.Register
  )
  //io.memory_read_enable
  io.memory_read_enable := Mux(
    opcode === InstructionTypes.L , 1.U(1.W) , 0.U(1.W)
  )
  //io.memory_write_enable
  io.memory_write_enable := Mux(
    opcode === InstructionTypes.S , 1.U(1.W) , 0.U(1.W)
  )
  //io.wb_reg_write_source
  io.wb_reg_write_source:=RegWriteSource.ALUResult //默认等于0
  when(opcode === InstructionTypes.RM || opcode === InstructionTypes.I
    || opcode === Instructions.lui || opcode === Instructions.auipc){
    io.wb_reg_write_source := RegWriteSource.ALUResult
  }
    .elsewhen(opcode === InstructionTypes.L){
      io.wb_reg_write_source := RegWriteSource.Memory
    }
    .elsewhen(opcode === Instructions.jal || opcode === Instructions.jalr){
      io.wb_reg_write_source := RegWriteSource.NextInstructionAddress
    }








  // lab1(InstructionDecode) end
  io.reg_write_enable := (opcode === InstructionTypes.RM) || (opcode === InstructionTypes.I) ||
    (opcode === InstructionTypes.L) || (opcode === Instructions.auipc) || (opcode === Instructions.lui) ||
    (opcode === Instructions.jal) || (opcode === Instructions.jalr)
  io.reg_write_address := io.instruction(11, 7)
}
