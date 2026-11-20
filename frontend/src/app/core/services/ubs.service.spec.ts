import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ItemEstoque } from '../models/estoque.model';
import { Medicamento } from '../models/medicamento.model';
import { UbsService } from './ubs.service';

const item = (id: number) =>
  ({ medicamento: { id } as Medicamento, status: 'DISPONIVEL', quantidade: 30 }) as ItemEstoque;

/** Página `n` (de `total` páginas) do estoque, com os itens informados. */
const pagina = (n: number, total: number, itens: ItemEstoque[]) => ({
  itens,
  pagina: n,
  tamanho: 100,
  totalItens: itens.length,
  totalPaginas: total,
});

const url = (n: number) => `/api/ubs/7/estoque?pagina=${n}&tamanho=100`;

describe('UbsService', () => {
  let service: UbsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UbsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('estoque junta todas as páginas, em ordem', () => {
    let recebido: ItemEstoque[] | undefined;
    service.estoque(7).subscribe((itens) => (recebido = itens));

    http.expectOne(url(0)).flush(pagina(0, 3, [item(1), item(2)]));
    http.expectOne(url(1)).flush(pagina(1, 3, [item(3), item(4)]));
    http.expectOne(url(2)).flush(pagina(2, 3, [item(5)]));

    expect(recebido?.map((i) => i.medicamento.id)).toEqual([1, 2, 3, 4, 5]);
  });

  it('estoque com uma página faz uma única chamada', () => {
    let recebido: ItemEstoque[] | undefined;
    service.estoque(7).subscribe((itens) => (recebido = itens));

    http.expectOne(url(0)).flush(pagina(0, 1, [item(1)]));

    expect(recebido?.length).toBe(1);
  });

  it('estoque vazio devolve lista vazia', () => {
    let recebido: ItemEstoque[] | undefined;
    service.estoque(7).subscribe((itens) => (recebido = itens));

    http.expectOne(url(0)).flush(pagina(0, 0, []));

    expect(recebido).toEqual([]);
  });
});
