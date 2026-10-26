export interface Medicamento {
  id: number;
  /** Código CATMAT (Catálogo de Materiais do Governo Federal). */
  catmat: string;
  nome: string;
  concentracao: string;
  formaFarmaceutica: string;
  categoria: string;
  /** Item da RENAME (Relação Nacional de Medicamentos Essenciais). */
  rename: boolean;
}
